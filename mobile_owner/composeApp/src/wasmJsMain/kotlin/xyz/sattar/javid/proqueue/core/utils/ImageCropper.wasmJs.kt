package xyz.sattar.javid.proqueue.core.utils

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/**
 * Not on the login/business-list path this phase targets (logo upload is
 * CreateBusinessRoute, out of scope — docs/OWNER_WEB_PLAN.md section 4, row
 * 3) but implemented for real via Skia (the same graphics library Compose
 * wasmJs itself renders through, `org.jetbrains.skia.*` from skiko) rather
 * than the browser Canvas API the plan sketches — it does the same job
 * (decode, crop a rect, scale) without any DOM element, and the crop math is
 * the same fraction-of-source math the Android actual uses.
 */
actual fun cropImageToSquare(
    source: ByteArray,
    leftFraction: Float,
    topFraction: Float,
    widthFraction: Float,
    heightFraction: Float,
    outputSize: Int
): ByteArray? {
    val decoded = try {
        Image.makeFromEncoded(source)
    } catch (e: Throwable) {
        return null
    }

    return try {
        val left = (leftFraction * decoded.width).roundToInt().coerceIn(0, max(0, decoded.width - 1))
        val top = (topFraction * decoded.height).roundToInt().coerceIn(0, max(0, decoded.height - 1))

        val requested = min(
            (widthFraction * decoded.width).roundToInt(),
            (heightFraction * decoded.height).roundToInt()
        )
        val side = requested
            .coerceAtMost(min(decoded.width - left, decoded.height - top))
            .coerceAtLeast(1)

        val surface = Surface.makeRasterN32Premul(outputSize, outputSize)
        val canvas = surface.canvas
        canvas.drawImageRect(
            image = decoded,
            src = Rect.makeXYWH(left.toFloat(), top.toFloat(), side.toFloat(), side.toFloat()),
            dst = Rect.makeXYWH(0f, 0f, outputSize.toFloat(), outputSize.toFloat())
        )
        val snapshot = surface.makeImageSnapshot()
        snapshot.encodeToData(EncodedImageFormat.JPEG, 90)?.bytes
    } catch (e: Throwable) {
        null
    }
}
