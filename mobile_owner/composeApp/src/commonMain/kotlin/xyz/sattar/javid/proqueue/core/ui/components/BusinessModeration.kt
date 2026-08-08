package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.business.ModerationStatus

/**
 * Moderation surfaces shared by the business list, the profile screen and the
 * edit screen.
 *
 * Colours follow the same light/dark pairs as [StatusBadge] in the appointment
 * list so a "pending" business reads the same as a "pending" appointment. This
 * is *not* the plan/subscription state — that keeps its own badge.
 */
private data class ModerationVisuals(
    val label: String,
    val icon: ImageVector,
    val background: Color,
    val content: Color
)

@Composable
private fun visualsFor(status: ModerationStatus, isDark: Boolean): ModerationVisuals = when (status) {
    ModerationStatus.PENDING -> ModerationVisuals(
        label = "در انتظار تأیید",
        icon = Icons.Rounded.HourglassTop,
        background = if (isDark) Color(0xFFE65100).copy(alpha = 0.4f) else Color(0xFFFFF3E0),
        content = if (isDark) Color(0xFFFFCC80) else Color(0xFFE65100)
    )

    ModerationStatus.APPROVED -> ModerationVisuals(
        label = "تأیید شده",
        icon = Icons.Rounded.Verified,
        background = if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFE8F5E9),
        content = if (isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
    )

    ModerationStatus.REJECTED -> ModerationVisuals(
        label = "تأیید نشده",
        icon = Icons.Rounded.ErrorOutline,
        background = if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) else Color(0xFFFFEBEE),
        content = if (isDark) Color(0xFFEF9A9A) else Color(0xFFC62828)
    )

    ModerationStatus.SUSPENDED -> ModerationVisuals(
        label = "تعلیق شده",
        icon = Icons.Rounded.Block,
        background = if (isDark) Color(0xFF37474F).copy(alpha = 0.5f) else Color(0xFFECEFF1),
        content = if (isDark) Color(0xFFB0BEC5) else Color(0xFF455A64)
    )
}

@Composable
private fun isDarkSurface(): Boolean = !MaterialTheme.colorScheme.surface.let { color ->
    (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.5
}

/**
 * Compact pill for list rows. Renders nothing when the server didn't report a
 * moderation status (older backend) — see [ModerationStatus.fromString].
 */
@Composable
fun ModerationBadge(
    status: ModerationStatus?,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    if (status == null) return
    val visuals = visualsFor(status, isDarkSurface())

    Surface(
        modifier = modifier,
        color = visuals.background,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = visuals.icon,
                contentDescription = null,
                tint = visuals.content,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                // The server ships a ready-made Persian label; fall back to our
                // own so the badge still reads correctly if it's missing.
                text = label?.takeIf { it.isNotBlank() } ?: visuals.label,
                style = MaterialTheme.typography.labelSmall,
                color = visuals.content,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Convenience overload so callers can just hand over the business. */
@Composable
fun ModerationBadge(business: Business, modifier: Modifier = Modifier) {
    ModerationBadge(
        status = business.moderationStatus,
        modifier = modifier,
        label = business.moderationStatusDisplay
    )
}

/**
 * Full-width explanation card for the profile/detail screen.
 *
 * Nothing is shown for an approved business (its badge is enough) or when the
 * backend reports no status at all — the banner is reserved for the states that
 * actually cost the owner bookings.
 *
 * @param onEditClick when non-null, a rejected/suspended business gets an
 *   "edit and resubmit" action so the owner isn't left guessing what to do.
 */
@Composable
fun ModerationBanner(
    business: Business,
    modifier: Modifier = Modifier,
    onEditClick: (() -> Unit)? = null
) {
    val status = business.moderationStatus ?: return
    if (status == ModerationStatus.APPROVED) return

    val visuals = visualsFor(status, isDarkSurface())
    val title = when (status) {
        ModerationStatus.PENDING -> "در انتظار تأیید ادمین"
        ModerationStatus.REJECTED -> "کسب‌وکار شما تأیید نشد"
        ModerationStatus.SUSPENDED -> "کسب‌وکار شما تعلیق شده است"
        ModerationStatus.APPROVED -> ""
    }
    val body = when (status) {
        ModerationStatus.PENDING ->
            "کسب‌وکار شما در صف بررسی است و تا زمان تأیید، برای مراجعین نمایش داده نمی‌شود."

        ModerationStatus.REJECTED ->
            "تا زمان اصلاح و تأیید مجدد، کسب‌وکار شما برای مراجعین نمایش داده نمی‌شود."

        ModerationStatus.SUSPENDED ->
            "نمایش کسب‌وکار شما برای مراجعین متوقف شده است."

        ModerationStatus.APPROVED -> ""
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = visuals.background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = visuals.icon,
                contentDescription = null,
                tint = visuals.content,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = visuals.content
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = visuals.content.copy(alpha = 0.9f)
                )

                // The reviewer's reason is the single most useful thing here, so
                // it gets its own raised block rather than a trailing sentence.
                if (business.moderationNote.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "دلیل بررسی‌کننده:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = business.moderationNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (status == ModerationStatus.PENDING && business.moderationSubmittedAt > 0) {
                    Text(
                        text = "ارسال برای بررسی: ${DateTimeUtils.getJalaliDate(business.moderationSubmittedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = visuals.content.copy(alpha = 0.8f)
                    )
                }

                if (onEditClick != null && status.needsOwnerAction) {
                    TextButton(
                        onClick = onEditClick,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 4.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = visuals.content,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ویرایش و ارسال مجدد",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = visuals.content
                        )
                    }
                }
            }
        }
    }
}
