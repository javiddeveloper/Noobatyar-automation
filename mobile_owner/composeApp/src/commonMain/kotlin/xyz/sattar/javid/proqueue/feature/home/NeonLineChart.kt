package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A neon-styled line chart of daily appointment counts. Pure Compose Canvas —
 * the glow is faked with layered strokes (cross-platform, no blur filter).
 *
 * [dayLabels] are drawn under their own data point rather than as an evenly
 * spaced Row beneath the canvas: the points sit at `padH .. width - padH`, so
 * a Row of equal-weight cells would centre each label in its cell and drift
 * further out of line with its point the closer it got to either edge.
 *
 * [fillHeight] is opt-in because the chart area uses `weight` when it's on.
 * A weighted child of a wrap-content Column measures to zero height, so this
 * may only be set by a caller that actually gives the chart a bounded height
 * (the web dashboard, whose row equalises both cards); the phone leaves it
 * off and keeps the fixed [chartHeight].
 */
@Composable
fun NeonLineChart(
    counts: List<Int>,
    modifier: Modifier = Modifier,
    dayLabels: List<String> = emptyList(),
    title: String = "روند نوبت‌های ۷ روز اخیر",
    chartHeight: Dp = 120.dp,
    fillHeight: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val today = counts.lastOrNull() ?: 0
    val peak = counts.maxOrNull() ?: 0
    val total = counts.sum()
    val textMeasurer = rememberTextMeasurer()

    Card(
        modifier = modifier.fillMaxWidth().let {
            if (onClick != null) it.clickable(onClick = onClick) else it
        },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.ShowChart,
                        contentDescription = null,
                        tint = lineColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "امروز: $today",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = lineColor
                )
            }

            // Totals read at a glance without having to trace the curve. Kept
            // to the two numbers an owner actually acts on — the week's volume
            // and its busiest day — rather than a full legend.
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartStat(label = "مجموع هفته", value = "$total", color = axisColor)
                ChartStat(label = "بیشترین روز", value = "$peak", color = axisColor)
            }

            Spacer(modifier = Modifier.height(12.dp))

            val chartModifier = Modifier
                .fillMaxWidth()
                .then(
                    if (fillHeight) Modifier.weight(1f).heightIn(min = chartHeight)
                    else Modifier.height(chartHeight)
                )

            if (counts.size < 2 || peak == 0) {
                Box(modifier = chartModifier, contentAlignment = Alignment.Center) {
                    Text(
                        text = "هنوز داده‌ای برای نمایش نیست",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val labelStyle = TextStyle(
                    fontSize = 10.sp,
                    color = axisColor,
                    fontWeight = FontWeight.Medium
                )
                val todayLabelStyle = labelStyle.copy(color = lineColor, fontWeight = FontWeight.Bold)

                Canvas(modifier = chartModifier) {
                    val w = size.width
                    // Room for the weekday row, so the curve never overlaps it.
                    val labelBand = if (dayLabels.isEmpty()) 0f else 18.dp.toPx()
                    val h = size.height - labelBand
                    val padTop = 14.dp.toPx()
                    val padBottom = 10.dp.toPx()
                    val padH = 10.dp.toPx()
                    val maxV = peak.coerceAtLeast(1).toFloat()
                    val n = counts.size
                    val stepX = (w - padH * 2) / (n - 1)
                    val baseY = h - padBottom

                    val pts = (0 until n).map { i ->
                        val x = padH + stepX * i
                        val norm = counts[i] / maxV
                        val y = baseY - norm * (h - padTop - padBottom)
                        Offset(x, y)
                    }

                    // Dashed guide at the busiest day, so every other point is
                    // read against something instead of floating on its own.
                    val peakY = baseY - (h - padTop - padBottom)
                    drawLine(
                        color = gridColor.copy(alpha = 0.45f),
                        start = Offset(padH, peakY),
                        end = Offset(w - padH, peakY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
                    )
                    drawLine(
                        color = gridColor.copy(alpha = 0.45f),
                        start = Offset(padH, baseY),
                        end = Offset(w - padH, baseY),
                        strokeWidth = 1.dp.toPx()
                    )

                    val line = smoothPath(pts)

                    // Soft gradient fill under the smooth line
                    val fill = smoothPath(pts).apply {
                        lineTo(pts.last().x, baseY)
                        lineTo(pts.first().x, baseY)
                        close()
                    }
                    drawPath(
                        fill,
                        brush = Brush.verticalGradient(
                            listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0f)),
                            endY = baseY
                        )
                    )

                    // Neon glow — layered strokes, wide & faint to narrow & bright
                    listOf(20f to 0.04f, 13f to 0.07f, 8f to 0.12f).forEach { (width, a) ->
                        drawPath(
                            line,
                            color = lineColor.copy(alpha = a),
                            style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // Main crisp line
                    drawPath(
                        line,
                        brush = Brush.horizontalGradient(
                            listOf(lineColor, lineColor.copy(alpha = 0.75f))
                        ),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // A marker on every day, not just the last: without them the
                    // smoothed curve gives no cue where one day ends and the
                    // next begins.
                    pts.dropLast(1).forEach { p ->
                        drawCircle(color = lineColor.copy(alpha = 0.28f), radius = 4.dp.toPx(), center = p)
                        drawCircle(color = lineColor, radius = 2.dp.toPx(), center = p)
                    }

                    // Glowing endpoint (today)
                    val last = pts.last()
                    drawCircle(color = lineColor.copy(alpha = 0.22f), radius = 11.dp.toPx(), center = last)
                    drawCircle(color = lineColor, radius = 4.dp.toPx(), center = last)
                    drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = last)

                    // Weekday ticks, each centred on its own point.
                    if (dayLabels.isNotEmpty()) {
                        dayLabels.take(n).forEachIndexed { i, label ->
                            if (label.isEmpty()) return@forEachIndexed
                            val isToday = i == n - 1
                            val measured = textMeasurer.measure(
                                AnnotatedString(label),
                                style = if (isToday) todayLabelStyle else labelStyle
                            )
                            drawText(
                                textLayoutResult = measured,
                                topLeft = Offset(
                                    x = pts[i].x - measured.size.width / 2f,
                                    y = h + (labelBand - measured.size.height) / 2f
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One "label: value" pair in the chart header strip. */
@Composable
private fun ChartStat(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.75f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Builds a smooth cubic-bezier path through the given points.
 * Control points are placed at 1/3 distance between consecutive points,
 * which gives a natural curve without overshooting.
 */
private fun smoothPath(pts: List<Offset>): Path = Path().apply {
    if (pts.isEmpty()) return@apply
    moveTo(pts[0].x, pts[0].y)
    if (pts.size == 1) return@apply
    for (i in 0 until pts.size - 1) {
        val p0 = pts[i]
        val p1 = pts[i + 1]
        val dx = (p1.x - p0.x) / 3f
        cubicTo(
            p0.x + dx, p0.y,
            p1.x - dx, p1.y,
            p1.x, p1.y
        )
    }
}
