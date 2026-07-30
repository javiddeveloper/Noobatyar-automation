package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

enum class ToastyType {
    Success, Error, Info, Warning
}

/**
 * A custom Host that acts like SnackbarHost but displays toasts at the top of the screen
 * using a Popup overlay, so it doesn't matter where it is placed in the layout hierarchy.
 */
@Composable
fun ToastyHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    defaultType: ToastyType = ToastyType.Error
) {
    val currentSnackbarData = hostState.currentSnackbarData
    
    // We use a Popup to break out of Scaffold's bottom constraints and display at the top.
    if (currentSnackbarData != null) {
        Popup(
            alignment = Alignment.TopCenter,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            AnimatedVisibility(
                visible = currentSnackbarData != null,
                enter = slideInVertically(initialOffsetY = { -it - 50 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it - 50 }) + fadeOut(),
                modifier = modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val (backgroundColor, contentColor, icon) = getToastyColorsAndIcon(defaultType)
                
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = backgroundColor,
                        contentColor = contentColor
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    onClick = { currentSnackbarData.dismiss() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor
                        )
                        Text(
                            text = currentSnackbarData.visuals.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = contentColor
                        )
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
