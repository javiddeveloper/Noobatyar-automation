package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

enum class ToastyType {
    Success, Error, Info, Warning
}

/**
 * A single UI-facing message paired with the styling it should render with.
 *
 * Every `message: String?` field across the app's State classes used to lose the
 * distinction between "this succeeded" and "this failed" — every toast rendered
 * with [ToastyHost]'s fixed per-screen `defaultType`, which nobody ever
 * overrode, so every message (including the app's rare success confirmations)
 * showed with the red error style. [UiMessage] carries the type with the text
 * itself, so a screen never has to guess.
 */
data class UiMessage(val text: String, val type: ToastyType = ToastyType.Error) {
    companion object {
        fun error(text: String) = UiMessage(text, ToastyType.Error)
        fun success(text: String) = UiMessage(text, ToastyType.Success)
        fun info(text: String) = UiMessage(text, ToastyType.Info)
        fun warning(text: String) = UiMessage(text, ToastyType.Warning)
    }
}

/** [SnackbarVisuals] that carries a [ToastyType] alongside the message text. */
private data class ToastyVisuals(
    override val message: String,
    val type: ToastyType,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short
) : SnackbarVisuals

/**
 * Shows [message] with its own [UiMessage.type] rather than the host's fixed
 * default — this is the one function every screen should call to surface a
 * toast, so the color is always correct regardless of what [ToastyHost.defaultType]
 * happens to be set to.
 */
suspend fun SnackbarHostState.showToasty(message: UiMessage) {
    showSnackbar(
        ToastyVisuals(message = message.text, type = message.type)
    )
}

suspend fun SnackbarHostState.showToasty(text: String, type: ToastyType) {
    showToasty(UiMessage(text, type))
}

/**
 * A custom Host that acts like SnackbarHost but displays toasts at the top of the screen
 * using a Popup overlay, so it doesn't matter where it is placed in the layout hierarchy.
 *
 * [defaultType] only applies to snackbars shown via the plain
 * `showSnackbar(message: String)` API (which carries no type); anything shown
 * via [showToasty] renders with its own type regardless of this default.
 */
@Composable
fun ToastyHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    defaultType: ToastyType = ToastyType.Error
) {
    if (hostState.currentSnackbarData != null) {
        // On a phone a toast spans the width because there is no width to
        // spare. On a desktop browser the same rule produces a 1900px banner
        // across the top of the window for a one-line message — so the wide
        // layouts pin it to the top *start* corner (the right, under the
        // app's forced RTL) at a readable measure instead.
        val isCompact = LocalWindowSize.current == WindowSize.Compact
        Popup(
            alignment = if (isCompact) Alignment.TopCenter else Alignment.TopStart,
            properties = PopupProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Box(
                modifier = Modifier
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(top = 16.dp)
            ) {
                SnackbarHost(
                    hostState = hostState,
                    modifier = if (isCompact) {
                        modifier.fillMaxWidth()
                    } else {
                        modifier.widthIn(max = 420.dp)
                    }
                ) { data ->
                    val type = (data.visuals as? ToastyVisuals)?.type ?: defaultType
                    val (backgroundColor, contentColor, icon) = getToastyColorsAndIcon(type)

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = backgroundColor.copy(alpha = 0.98f),
                            contentColor = contentColor
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .then(if (isCompact) Modifier.fillMaxWidth() else Modifier),
                        onClick = { data.dismiss() }
                    ) {
                        Row(
                            modifier = Modifier
                                .then(if (isCompact) Modifier.fillMaxWidth() else Modifier)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = contentColor.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = data.visuals.message,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = 20.sp
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getToastyColorsAndIcon(type: ToastyType): Triple<Color, Color, ImageVector> {
    return when (type) {
        ToastyType.Success -> Triple(
            Color(0xFF4CAF50),
            Color.White,
            Icons.Rounded.CheckCircle
        )
        ToastyType.Error -> Triple(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError,
            Icons.Rounded.ErrorOutline
        )
        ToastyType.Info -> Triple(
            Color(0xFF2196F3),
            Color.White,
            Icons.Rounded.Info
        )
        ToastyType.Warning -> Triple(
            Color(0xFFFFC107),
            Color.Black,
            Icons.Rounded.Warning
        )
    }
}
