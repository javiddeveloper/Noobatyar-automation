package xyz.sattar.javid.proqueue.core.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

actual fun cropImageToSquare(
    source: ByteArray,
    leftFraction: Float,
    topFraction: Float,
    widthFraction: Float,
    heightFraction: Float,
    outputSize: Int
): ByteArray? {
    val decoded = BitmapFactory.decodeByteArray(source, 0, source.size) ?: return null

    return try {
        val left = (leftFraction * decoded.width).roundToInt().coerceIn(0, max(0, decoded.width - 1))
        val top = (topFraction * decoded.height).roundToInt().coerceIn(0, max(0, decoded.height - 1))

        // The window is square on screen, so take the smaller of the two edges
        // and clamp it to what is actually left of the bitmap.
        val requested = min(
            (widthFraction * decoded.width).roundToInt(),
            (heightFraction * decoded.height).roundToInt()
        )
        val side = requested
            .coerceAtMost(min(decoded.width - left, decoded.height - top))
            .coerceAtLeast(1)

        val cropped = Bitmap.createBitmap(decoded, left, top, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, outputSize, outputSize, true)

        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }.also {
            if (scaled !== cropped) scaled.recycle()
            if (cropped !== decoded) cropped.recycle()
        }
    } catch (e: Exception) {
        null
    } finally {
        decoded.recycle()
    }
}
