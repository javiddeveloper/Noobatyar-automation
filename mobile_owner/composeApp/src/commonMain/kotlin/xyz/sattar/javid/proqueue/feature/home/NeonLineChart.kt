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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A neon-styled line chart of daily appointment counts. Pure Compose Canvas —
 * the glow is faked with layered strokes (cross-platform, no blur filter).
 */
@Composable
fun NeonLineChart(
    counts: List<Int>,
    modifier: Modifier = Modifier,
    title: String = "روند نوبت‌های ۷ روز اخیر",
    onClick: (() -> Unit)? = null
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val today = counts.lastOrNull() ?: 0
    val peak = counts.maxOrNull() ?: 0

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
        Column(modifier = Modifier.padding(16.dp)) {
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

            Spacer(modifier = Modifier.height(16.dp))

            if (counts.size < 2 || peak == 0) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "هنوز داده‌ای برای نمایش نیست",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                    val w = size.width
                    val h = size.height
                    val padTop = 14.dp.toPx()
                    val padBottom = 10.dp.toPx()
                    val padH = 6.dp.toPx()
                    val maxV = peak.coerceAtLeast(1).toFloat()
                    val n = counts.size
                    val stepX = (w - padH * 2) / (n - 1)

                    val pts = (0 until n).map { i ->
                        val x = padH + stepX * i
                        val norm = counts[i] / maxV
                        val y = h - padBottom - norm * (h - padTop - padBottom)
                        Offset(x, y)
                    }

                    val line = smoothPath(pts)

                    // Soft gradient fill under the smooth line
                    val fill = smoothPath(pts).apply {
                        lineTo(pts.last().x, h - padBottom)
                        lineTo(pts.first().x, h - padBottom)
                        close()
                    }
                    drawPath(
                        fill,
                        brush = Brush.verticalGradient(
                            listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0f))
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

                    // Glowing endpoint
                    val last = pts.last()
                    drawCircle(color = lineColor.copy(alpha = 0.22f), radius = 11.dp.toPx(), center = last)
                    drawCircle(color = lineColor, radius = 4.dp.toPx(), center = last)
                    drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = last)
                }
            }
        }
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
