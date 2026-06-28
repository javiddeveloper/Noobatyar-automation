package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.complete_action
import proqueue.composeapp.generated.resources.contact_options
import proqueue.composeapp.generated.resources.delete_appointment
import proqueue.composeapp.generated.resources.no_show_action
import proqueue.composeapp.generated.resources.phone_call
import proqueue.composeapp.generated.resources.sms
import proqueue.composeapp.generated.resources.telegram
import proqueue.composeapp.generated.resources.to_label
import proqueue.composeapp.generated.resources.whatsapp
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.formatPhoneNumberForAction
import xyz.sattar.javid.proqueue.core.utils.openPhoneDial
import xyz.sattar.javid.proqueue.core.utils.openSms
import xyz.sattar.javid.proqueue.core.utils.openTelegram
import xyz.sattar.javid.proqueue.core.utils.openWhatsApp
import xyz.sattar.javid.proqueue.feature.home.QueueItem

@Composable
fun QueueItemCard(
    item: QueueItem,
    onRemove: () -> Unit,
    onComplete: () -> Unit,
    onNoShow: () -> Unit,
    onSendMessage: (appointmentId: Long, type: String, content: String, businessTitle: String) -> Unit,
    onItemClick: () -> Unit = {},
    onGenerateMessage: (Long, String, String, String, Long, String, Int?) -> String
) {
    val isDark = !MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.5
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onItemClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val dateText = DateTimeUtils.formatDateTime(item.estimatedStartTime)
            val startTimeOnly = DateTimeUtils.formatTime(item.estimatedStartTime)
            val endTimeOnly = DateTimeUtils.formatTime(item.estimatedEndTime)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.visitorName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.visitorPhone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$endTimeOnly ${stringResource(Res.string.to_label)} $startTimeOnly",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val badgeBgColor = when {
                    item.overdue -> if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) else Color(0xFFFFEBEE)
                    else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.4f else 0.7f)
                }
                val badgeContentColor = when {
                    item.overdue -> if (isDark) Color(0xFFEF9A9A) else Color(0xFFC62828)
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBgColor
                ) {
                    Text(
                        text = item.waitingText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeContentColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var showMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(Res.string.contact_options)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.sms)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Message, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            val business = BusinessStateHolder.selectedBusiness.value
                            val businessTitle = business?.title ?: "--"
                            val businessAddress = business?.address ?: "--"
                            val message = onGenerateMessage(
                                /* businessId = */ item.appointment.businessId,
                                /* businessTitle = */ businessTitle,
                                /* businessAddress = */ businessAddress,
                                /* visitorName = */ item.visitorName,
                                /* appointmentMillis = */ item.appointment.appointmentDate,
                                /* reminderMinutes = */ item.waitingText,
                                /* serviceDuration = */ item.appointment.serviceDuration,
                            )
                            openSms(formatPhoneNumberForAction(item.visitorPhone), message)
                            onSendMessage(item.appointment.id, "SMS", message, businessTitle)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.whatsapp)) },
                        leadingIcon = {
                            Icon(
                                painterResource(Res.drawable.whatsapp),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            val business = BusinessStateHolder.selectedBusiness.value
                            val businessTitle = business?.title ?: "--"
                            val businessAddress = business?.address ?: "--"
                            val message = onGenerateMessage(
                                /* businessId = */ item.appointment.businessId,
                                /* businessTitle = */ businessTitle,
                                /* businessAddress = */ businessAddress,
                                /* visitorName = */ item.visitorName,
                                /* appointmentMillis = */ item.appointment.appointmentDate,
                                /* reminderMinutes = */ item.waitingText,
                                /* serviceDuration = */ item.appointment.serviceDuration,
                                )
                            openWhatsApp(formatPhoneNumberForAction(item.visitorPhone), message)
                            onSendMessage(item.appointment.id, "WHATSAPP", message, businessTitle)
                        })
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.telegram)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            val business = BusinessStateHolder.selectedBusiness.value
                            val businessTitle = business?.title ?: "--"
                            val businessAddress = business?.address ?: "--"
                            val message = onGenerateMessage(
                                /* businessId = */ item.appointment.businessId,
                                /* businessTitle = */ businessTitle,
                                /* businessAddress = */ businessAddress,
                                /* visitorName = */ item.visitorName,
                                /* appointmentMillis = */ item.appointment.appointmentDate,
                                /* reminderMinutes = */ item.waitingText,
                                /* serviceDuration = */ item.appointment.serviceDuration,
                            )
                            openTelegram(formatPhoneNumberForAction(item.visitorPhone), message)
                            onSendMessage(item.appointment.id, "TELEGRAM", message, businessTitle)
                        })
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.phone_call)) },
                        leadingIcon = { Icon(Icons.Rounded.Call, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            openPhoneDial(item.visitorPhone)
                        })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            modifier = Modifier.clickable { onComplete() },
                            contentDescription = stringResource(Res.string.complete_action),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(Res.string.complete_action),
                            modifier = Modifier.clickable { onComplete() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            modifier = Modifier.clickable { onNoShow() },
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(Res.string.no_show_action),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(Res.string.no_show_action),
                            modifier = Modifier.clickable { onNoShow() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            modifier = Modifier.clickable { onRemove() },
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(Res.string.delete_appointment),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(Res.string.delete_appointment),
                            modifier = Modifier.clickable { onRemove() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
