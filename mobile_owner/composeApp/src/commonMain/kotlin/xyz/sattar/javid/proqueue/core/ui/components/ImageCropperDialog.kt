package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.sattar.javid.proqueue.core.utils.cropImageToSquare
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Circular crop dialog for the business logo.
 *
 * The photo is drawn scaled to *cover* a square window that the user can pan and
 * pinch; on confirm the window is converted to fractions of the source image and
 * handed to [cropImageToSquare]. Previously the picked file was uploaded as-is,
 * so a non-square photo was arbitrarily centre-cropped wherever it was shown and
 * the full-resolution original went over the wire.
 *
 * The image is drawn with an explicit [Canvas] rather than `Image` + `ContentScale`
 * so that what is displayed and what is cropped share one transform. The pan is
 * held as a fraction of the window rather than in pixels, which keeps the crop
 * independent of the window's on-screen size.
 */
@Composable
fun ImageCropperDialog(
    source: ByteArray,
    bitmap: ImageBitmap,
    onDismiss: () -> Unit,
    onCropped: (ByteArray) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isWorking by remember { mutableStateOf(false) }

    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(24.dp)
                    // Without this the photo is drawn past the square and spills
                    // across the screen undimmed, outside the scrim.
                    .clipToBounds()
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, dragged, gestureZoom, _ ->
                            val window = min(size.width, size.height).toFloat()
                            if (window <= 0f) return@detectTransformGestures
                            val newZoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                            zoom = newZoom
                            pan = clampPan(
                                candidate = pan + Offset(dragged.x / window, dragged.y / window),
                                bitmap = bitmap,
                                zoom = newZoom
                            )
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val window = size.minDimension
                    val scale = max(window / bitmap.width, window / bitmap.height) * zoom
                    val shownW = bitmap.width * scale
                    val shownH = bitmap.height * scale

                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bitmap.width, bitmap.height),
                        dstOffset = IntOffset(
                            (center.x - shownW / 2f + pan.x * window).roundToInt(),
                            (center.y - shownH / 2f + pan.y * window).roundToInt()
                        ),
                        dstSize = IntSize(shownW.roundToInt(), shownH.roundToInt())
                    )

                    // Dim everything outside the circular window.
                    val scrim = Path().apply {
                        addRect(Rect(Offset.Zero, Size(size.width, size.height)))
                        addOval(Rect(center = center, radius = window / 2f))
                        fillType = PathFillType.EvenOdd
                    }
                    drawPath(scrim, Color.Black.copy(alpha = 0.6f))
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = window / 2f,
                        center = center,
                        style = Stroke(width = 2f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, enabled = !isWorking) {
                    Icon(Icons.Rounded.Close, contentDescription = "بستن", tint = Color.White)
                }
                Text(
                    text = "برش لوگو",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "برای جابه‌جایی بکشید، با دو انگشت بزرگ‌نمایی کنید",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                AppButton(
                    text = "تأیید و برش",
                    onClick = {
                        if (isWorking) return@AppButton
                        isWorking = true
                        val window = cropWindow(bitmap, zoom, pan)
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                cropImageToSquare(
                                    source = source,
                                    leftFraction = window[0],
                                    topFraction = window[1],
                                    widthFraction = window[2],
                                    heightFraction = window[3]
                                )
                            }
                            isWorking = false
                            if (result != null) onCropped(result) else onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = isWorking
                )
            }
        }
    }
}

/**
 * Keeps the photo covering the window so the crop can never include empty space.
 * [candidate] and the result are fractions of the window's side.
 */
private fun clampPan(candidate: Offset, bitmap: ImageBitmap, zoom: Float): Offset {
    // Displayed size relative to the window: cover-scale makes the smaller edge
    // exactly 1.0, so the larger edge is the aspect ratio.
    val cover = max(1f / bitmap.width, 1f / bitmap.height) * zoom
    val maxX = max(0f, (bitmap.width * cover - 1f) / 2f)
    val maxY = max(0f, (bitmap.height * cover - 1f) / 2f)
    return Offset(
        candidate.x.coerceIn(-maxX, maxX),
        candidate.y.coerceIn(-maxY, maxY)
    )
}

/**
 * Converts the current transform into the crop window, as
 * `[left, top, width, height]` fractions of the source image.
 *
 * At zoom 1 the window spans the image's shorter edge, so the visible square is
 * `min(w, h) / zoom` source pixels on a side; [pan] then slides it by that many
 * pixels per unit. The window's on-screen size cancels out entirely.
 */
private fun cropWindow(bitmap: ImageBitmap, zoom: Float, pan: Offset): FloatArray {
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val side = min(w, h) / zoom

    val leftPx = (w - side) / 2f - pan.x * side
    val topPx = (h - side) / 2f - pan.y * side

    val left = (leftPx / w).coerceIn(0f, 1f)
    val top = (topPx / h).coerceIn(0f, 1f)
    return floatArrayOf(
        left,
        top,
        (side / w).coerceIn(0f, 1f - left),
        (side / h).coerceIn(0f, 1f - top)
    )
}
