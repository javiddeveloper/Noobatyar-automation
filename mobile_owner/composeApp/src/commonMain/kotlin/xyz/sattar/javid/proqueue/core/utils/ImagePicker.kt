package xyz.sattar.javid.proqueue.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Handle returned by [rememberImagePicker]. Call [launch] to open the
 * platform's picker UI.
 */
interface ImagePickerLauncher {
    fun launch()
}

/**
 * Our own indirection over the platform image picker (peekaboo on
 * Android/iOS today). `feature/` code calls this instead of importing
 * `com.preat.peekaboo.image.picker.*` directly, so peekaboo stays confined to
 * androidMain/iosMain — a future web target can supply its own actual
 * (`<input type="file">`) without touching any feature code.
 *
 * Always single-selection: [onSingleImagePicked] fires once with the raw
 * bytes of the picked file, or is never called if the user cancels. The
 * caller (today, [xyz.sattar.javid.proqueue.feature.createBusiness.CreateBusinessRoute])
 * hands those bytes straight to the cropper, exactly as before.
 */
@Composable
expect fun rememberImagePicker(onSingleImagePicked: (ByteArray) -> Unit): ImagePickerLauncher

/**
 * Decodes raw image bytes into an [ImageBitmap] for preview/cropping, or
 * returns null if the bytes could not be decoded. Wraps the platform decoder
 * (peekaboo's `toImageBitmap()` on Android/iOS today) so `feature/` code
 * doesn't need to import peekaboo just to preview a picked photo.
 */
expect fun ByteArray.toImageBitmapOrNull(): ImageBitmap?
