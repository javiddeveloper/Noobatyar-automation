package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The Noobatyar mark: a checkmark inside an open ring (an appointment
 * confirmed, time coming round), with the single amber dot that is the brand's
 * one accent colour.
 *
 * Drawn rather than shipped as a drawable so it inherits [tint] from whatever
 * surface it sits on — the same reason the Django admin inlines it as SVG with
 * `currentColor` (backend/templates/admin/base_site.html). The geometry below
 * is that same artwork, authored in a 512x512 box and scaled to fit.
 *
 * The amber dot is deliberately NOT tinted: per docs/BRAND_GUIDE_FOR_AI.md it
 * is the one fixed accent and must not drift with the surrounding colour.
 */
@Composable
fun NoobatyarMark(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    accent: Color = Color(0xFFF59E0B)
) {
    Canvas(modifier = modifier) {
        // Uniform scale from the 512x512 authoring box, so the mark keeps its
        // proportions in whatever square (or rectangle) it is given.
        val s = minOf(size.width, size.height) / 512f
        val dx = (size.width - 512f * s) / 2f
        val dy = (size.height - 512f * s) / 2f
        fun x(v: Float) = dx + v * s
        fun y(v: Float) = dy + v * s

        val strokeWidth = 36f * s

        // Open ring. The gap sits at the top, where the dot goes — start and
        // sweep are the arc from the original path's endpoints (337.64,156.96)
        // to (174.36,156.96) around centre (256,256), radius 146.
        drawArc(
            color = tint,
            startAngle = -50.5f,
            sweepAngle = 281f,
            useCenter = false,
            topLeft = Offset(x(110f), y(110f)),
            size = Size(292f * s, 292f * s),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Checkmark.
        drawPath(
            path = Path().apply {
                moveTo(x(191f), y(276f))
                lineTo(x(234f), y(323f))
                lineTo(x(327f), y(221f))
            },
            color = tint,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        drawCircle(
            color = accent,
            radius = 28f * s,
            center = Offset(x(256f), y(122f))
        )
    }
}
