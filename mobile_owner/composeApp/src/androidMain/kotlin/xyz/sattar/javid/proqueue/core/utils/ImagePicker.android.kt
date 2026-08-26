package xyz.sattar.javid.proqueue.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import com.preat.peekaboo.image.picker.toImageBitmap

@Composable
actual fun rememberImagePicker(onSingleImagePicked: (ByteArray) -> Unit): ImagePickerLauncher {
    val scope = rememberCoroutineScope()
    val peekabooLauncher = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = scope,
        onResult = { byteArrays ->
            byteArrays.firstOrNull()?.let(onSingleImagePicked)
        }
    )
    return object : ImagePickerLauncher {
        override fun launch() = peekabooLauncher.launch()
    }
}

actual fun ByteArray.toImageBitmapOrNull(): ImageBitmap? = try {
    toImageBitmap()
} catch (e: Exception) {
    null
}
