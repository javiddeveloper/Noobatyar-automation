package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalUriHandler
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
    val uriHandler = LocalUriHandler.current
    val isPending = item.appointment.status == "PENDING_APPROVAL" || item.appointment.status == "PENDING_VERIFICATION"
    val isPendingVerification = item.appointment.status == "PENDING_VERIFICATION"

    // Build the reminder message once and dispatch it through the chosen channel.
    val sendVia: (String, (String, String) -> Unit) -> Unit = { channel, open ->
        val business = BusinessStateHolder.selectedBusiness.value
        val businessTitle = business?.title ?: "--"
        val businessAddress = business?.address ?: "--"
        val message = onGenerateMessage(
            item.appointment.businessId,
            businessTitle,
            businessAddress,
            item.visitorName,
            item.appointment.appointmentDate,
            item.waitingText,
            item.appointment.serviceDuration,
        )
        open(formatPhoneNumberForAction(item.visitorPhone), message)
        onSendMessage(item.appointment.id, channel, message, businessTitle)
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
            Spacer(modifier = Modifier.height(8.dp))

            // Footer: a single primary action, everything else tucked into one menu.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(Res.string.contact_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        // --- Contact the visitor ---
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.sms)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Message, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                sendVia("SMS", ::openSms)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.whatsapp)) },
                            leadingIcon = { Icon(painterResource(Res.drawable.whatsapp), contentDescription = null) },
                            onClick = {
                                showMenu = false
                                sendVia("WHATSAPP", ::openWhatsApp)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.telegram)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                sendVia("TELEGRAM", ::openTelegram)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.phone_call)) },
                            leadingIcon = { Icon(Icons.Rounded.Call, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                openPhoneDial(item.visitorPhone)
                            }
                        )

                        // --- Payment receipt (if any) ---
                        if (item.appointment.paymentReceipt != null) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("مشاهده فیش پرداخت") },
                                leadingIcon = { Icon(Icons.Rounded.Receipt, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val receipt = item.appointment.paymentReceipt
                                    val url = if (receipt.startsWith("http")) receipt
                                    else "${xyz.sattar.javid.proqueue.BuildKonfig.BASE_URL}$receipt"
                                    uriHandler.openUri(url)
                                }
                            )
                        }

                        // --- Destructive / secondary status actions ---
                        HorizontalDivider()
                        if (isPending) {
                            DropdownMenuItem(
                                text = { Text("رد کردن", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onNoShow()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.no_show_action), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onNoShow()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.delete_appointment), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onRemove()
                                }
                            )
                        }
                    }
                }

                // Primary action — the one thing an owner does most on a queue row.
                FilledTonalButton(
                    onClick = onComplete,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isPendingVerification -> "تأیید فیش"
                            isPending -> "تایید"
                            else -> stringResource(Res.string.complete_action)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
