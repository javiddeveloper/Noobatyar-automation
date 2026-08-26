package xyz.sattar.javid.proqueue.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.browser.document
import org.jetbrains.skia.Image
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.File
import org.w3c.files.FileReader

/**
 * Not used by the login/business-list path this phase targets (logo upload
 * is CreateBusinessRoute, out of scope — see docs/OWNER_WEB_PLAN.md section
 * 4, row 2) but implemented for real rather than stubbed, since it's cheap
 * and correct: a hidden `<input type="file">`, created fresh per [launch] so
 * picking the same file twice in a row still fires a change event.
 */
@Composable
actual fun rememberImagePicker(onSingleImagePicked: (ByteArray) -> Unit): ImagePickerLauncher {
    return remember {
        object : ImagePickerLauncher {
            override fun launch() {
                val input = document.createElement("input") as HTMLInputElement
                input.type = "file"
                input.accept = "image/*"
                input.style.display = "none"
                input.addEventListener("change", { event: Event ->
                    val file = input.files?.item(0)
                    document.body?.removeChild(input)
                    if (file != null) {
                        readFileAsBytes(file) { bytes -> onSingleImagePicked(bytes) }
                    }
                })
                document.body?.appendChild(input)
                input.click()
            }
        }
    }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun readFileAsBytes(file: File, onLoaded: (ByteArray) -> Unit) {
    val reader = FileReader()
    reader.onload = { _ ->
        val buffer = reader.result as ArrayBuffer
        onLoaded(Int8Array(buffer).toByteArray())
    }
    reader.readAsArrayBuffer(file)
}

actual fun ByteArray.toImageBitmapOrNull(): ImageBitmap? = try {
    Image.makeFromEncoded(this).toComposeImageBitmap()
} catch (e: Throwable) {
    null
}
