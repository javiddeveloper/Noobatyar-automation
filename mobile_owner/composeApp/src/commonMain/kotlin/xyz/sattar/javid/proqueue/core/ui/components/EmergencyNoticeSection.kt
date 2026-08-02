package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.notice_message_label
import proqueue.composeapp.generated.resources.notice_message_public_warning
import proqueue.composeapp.generated.resources.notice_section_title
import proqueue.composeapp.generated.resources.notice_switch_description
import proqueue.composeapp.generated.resources.notice_switch_label
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits

/** Backend limit on Business.notice_message — enforced here so the request can't be rejected. */
const val NOTICE_MESSAGE_MAX_LENGTH = 300

/**
 * The public "something is wrong today" banner: a switch plus the text clients
 * will read. Shared by the business-edit form and the profile/settings screen so
 * both spell out that the message is public — the owner is writing for their
 * clients, not leaving themselves a note.
 *
 * The text field only appears while the switch is on, because a message that
 * nobody can see is the one thing this feature must never look like.
 */
@Composable
fun EmergencyNoticeSection(
    enabled: Boolean,
    message: String,
    onEnabledChange: (Boolean) -> Unit,
    onMessageChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isEditable: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Campaign,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.notice_section_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = isEditable) { onEnabledChange(!enabled) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(Res.string.notice_switch_label),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                enabled = isEditable
            )
        }

        Text(
            text = stringResource(Res.string.notice_switch_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AnimatedVisibility(visible = enabled) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                AppTextField(
                    value = message,
                    // Truncate rather than reject: pasting a long text should
                    // keep the first 300 characters, not silently do nothing.
                    onValueChange = { onMessageChange(it.take(NOTICE_MESSAGE_MAX_LENGTH)) },
                    label = stringResource(Res.string.notice_message_label),
                    maxLine = 5,
                    maxLength = NOTICE_MESSAGE_MAX_LENGTH,
                    enabled = isEditable,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = stringResource(Res.string.notice_message_public_warning),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${message.length.toString().toPersianDigits()}/" +
                                NOTICE_MESSAGE_MAX_LENGTH.toString().toPersianDigits(),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.End,
                        color = if (message.length >= NOTICE_MESSAGE_MAX_LENGTH)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
