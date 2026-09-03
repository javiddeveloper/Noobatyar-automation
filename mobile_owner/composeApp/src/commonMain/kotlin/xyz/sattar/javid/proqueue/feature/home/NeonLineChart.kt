package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A neon line chart of daily appointment counts.
 *
 * Pure Compose Canvas — the glow is layered strokes rather than a blur filter,
 * so it renders identically on Android and iOS.
 *
 * This is the drawing only; the card, its title and any period switching belong
 * to the caller (see [HomeTrendCard]). It used to be a whole card, which meant
 * the chart could not be reused inside another one.
 */
@Composable
fun NeonLineCanvas(
    counts: List<Int>,
    modifier: Modifier = Modifier,
    height: Dp = 130.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    emptyLabel: String = "هنوز داده‌ای برای نمایش نیست",
) {
    val peak = counts.maxOrNull() ?: 0

    if (counts.size < 2 || peak == 0) {
        Box(
            modifier = modifier.fillMaxWidth().height(height),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emptyLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // Animated so switching period redraws as a sweep instead of a jump cut.
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(500),
        label = "chart",
    )

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
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
            val norm = (counts[i] / maxV) * progress
            val y = h - padBottom - norm * (h - padTop - padBottom)
            Offset(x, y)
        }

        // Gridlines and a baseline. Without them the line floats in space and
        // there is no way to read a height off it — a spike could be 2 or 20.
        val gridColor = lineColor.copy(alpha = 0.10f)
        val plotTop = padTop
        val plotBottom = h - padBottom
        listOf(0f, 0.5f, 1f).forEach { t ->
            val y = plotBottom - t * (plotBottom - plotTop)
            drawLine(
                color = if (t == 0f) lineColor.copy(alpha = 0.22f) else gridColor,
                start = Offset(padH, y),
                end = Offset(w - padH, y),
                strokeWidth = if (t == 0f) 1.5.dp.toPx() else 1.dp.toPx(),
            )
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

        // A dot per reading, so the curve is visibly sampled data rather than a
        // free-drawn shape, and a single busy day is a point you can pick out.
        if (n <= 14) {
            pts.forEach { p ->
                drawCircle(color = lineColor.copy(alpha = 0.9f), radius = 3.5.dp.toPx(), center = p)
                drawCircle(color = Color.White, radius = 1.6.dp.toPx(), center = p)
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
