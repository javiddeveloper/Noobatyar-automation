package xyz.sattar.javid.proqueue.core.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGImageCreateWithImageInRect
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalForeignApi::class)
actual fun cropImageToSquare(
    source: ByteArray,
    leftFraction: Float,
    topFraction: Float,
    widthFraction: Float,
    heightFraction: Float,
    outputSize: Int
): ByteArray? {
    if (source.isEmpty()) return null
    val image = UIImage.imageWithData(source.toNSData()) ?: return null

    // Bake in EXIF orientation first: UIImage.CGImage is stored in raw camera
    // orientation, so cropping it directly would use rotated coordinates.
    val pixelWidth = image.size.useContents { width } * image.scale
    val pixelHeight = image.size.useContents { height } * image.scale
    if (pixelWidth <= 0.0 || pixelHeight <= 0.0) return null

    UIGraphicsBeginImageContextWithOptions(CGSizeMake(pixelWidth, pixelHeight), false, 1.0)
    image.drawInRect(CGRectMake(0.0, 0.0, pixelWidth, pixelHeight))
    val normalized = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    val cgImage = normalized?.CGImage ?: return null

    val left = (leftFraction * pixelWidth).toDouble().coerceIn(0.0, max(0.0, pixelWidth - 1))
    val top = (topFraction * pixelHeight).toDouble().coerceIn(0.0, max(0.0, pixelHeight - 1))
    // The window is square on screen, so take the smaller edge and clamp it to
    // what is actually left of the image.
    val side = min(
        (widthFraction * pixelWidth).toDouble(),
        (heightFraction * pixelHeight).toDouble()
    ).coerceAtMost(min(pixelWidth - left, pixelHeight - top)).coerceAtLeast(1.0)

    val croppedRef = CGImageCreateWithImageInRect(
        cgImage,
        CGRectMake(left, top, side, side)
    ) ?: return null

    val side2 = outputSize.coerceAtLeast(1).toDouble()
    val cropped = UIImage.imageWithCGImage(croppedRef)
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(side2, side2), false, 1.0)
    cropped.drawInRect(CGRectMake(0.0, 0.0, side2, side2))
    val scaled = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    CGImageRelease(croppedRef)

    val jpeg = scaled?.let { UIImageJPEGRepresentation(it, 0.9) } ?: return null
    return jpeg.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
