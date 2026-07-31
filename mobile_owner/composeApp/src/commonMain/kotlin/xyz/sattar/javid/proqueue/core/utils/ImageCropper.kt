package xyz.sattar.javid.proqueue.core.utils

/**
 * Crops [source] to a square and re-encodes it as JPEG.
 *
 * The crop window is given as fractions of the source image (0f..1f) rather than
 * pixels, so the caller doesn't need to know how each platform decoded the file.
 * The UI measures against the [androidx.compose.ui.graphics.ImageBitmap] it is
 * displaying, and each platform maps those fractions onto its own decode — which
 * keeps the two in agreement even when EXIF orientation changes the pixel
 * dimensions.
 *
 * The result is downscaled to [outputSize] x [outputSize]; a logo is only ever
 * shown in a small circle, so uploading the full-resolution original is waste.
 *
 * Returns null if the bytes could not be decoded.
 */
expect fun cropImageToSquare(
    source: ByteArray,
    leftFraction: Float,
    topFraction: Float,
    widthFraction: Float,
    heightFraction: Float,
    outputSize: Int = 512
): ByteArray?
