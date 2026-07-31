package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

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
    if (hostState.currentSnackbarData != null) {
        Popup(
            alignment = Alignment.TopCenter,
            properties = PopupProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Box(modifier = Modifier.padding(top = 16.dp)) {
                SnackbarHost(
                    hostState = hostState,
                    modifier = modifier.fillMaxWidth()
                ) { data ->
                    val (backgroundColor, contentColor, icon) = getToastyColorsAndIcon(defaultType)
                    
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
                            .fillMaxWidth(),
                        onClick = { data.dismiss() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
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
