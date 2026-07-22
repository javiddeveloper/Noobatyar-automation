package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SubscriptionDto
import kotlin.time.ExperimentalTime

/**
 * Subscription status card, shown on the home screen. (Moved out of the profile
 * bottom sheet so the owner sees plan status front-and-center.)
 */
@Composable
fun SubscriptionCard(subscription: SubscriptionDto?) {
    val isValid = subscription?.isValid ?: false
    val plan = subscription?.plan
    val isVip = plan?.isVip ?: false
    val isTrial = plan?.name?.contains("آزمایشی") == true
    val isDark = !MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.5
    }

    val cardColor = when {
        !isValid -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        isVip -> if (isDark) Color(0xFF3E2723) else Color(0xFFFFF8E1)
        isTrial -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    }
    val starColor = when {
        !isValid -> MaterialTheme.colorScheme.onSurfaceVariant
        isVip -> Color(0xFFFFA000)
        isTrial -> Color.Gray
        else -> MaterialTheme.colorScheme.primary
    }
    val borderColor = when {
        !isValid -> null
        isVip -> Color(0xFFFFA000).copy(alpha = 0.5f)
        isTrial -> Color.Gray.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    }
    val contentColor = when {
        !isValid -> MaterialTheme.colorScheme.onSurfaceVariant
        isVip -> if (isDark) Color(0xFFFFE082) else Color(0xFF5D4037)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = contentColor),
        shape = RoundedCornerShape(16.dp),
        border = borderColor?.let { CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(it)) }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = if (isValid) Icons.Rounded.Star else Icons.Rounded.Info,
                    contentDescription = null,
                    tint = starColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isValid) "اشتراک فعال: ${plan?.name ?: ""}" else "فاقد اشتراک فعال",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isValid && subscription != null) {
                Spacer(modifier = Modifier.height(16.dp))
                SubscriptionInfoRow("تاریخ شروع:", formatIsoDate(subscription.startedAt))
                SubscriptionInfoRow("تاریخ انقضا:", formatIsoDate(subscription.endsAt))
                val daysRemaining = calculateDaysRemaining(subscription.endsAt)
                SubscriptionInfoRow(
                    label = "اعتبار باقی‌مانده:",
                    value = "$daysRemaining روز",
                    valueColor = if (daysRemaining < 7) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "برای استفاده از تمامی امکانات نوبت یار، نسبت به تهیه اشتراک اقدام کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun SubscriptionInfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@OptIn(ExperimentalTime::class)
private fun formatIsoDate(isoString: String?): String {
    if (isoString == null) return "--"
    return try {
        DateTimeUtils.formatDate(Instant.parse(isoString).toEpochMilliseconds())
    } catch (e: Exception) {
        "--"
    }
}

@OptIn(ExperimentalTime::class)
private fun calculateDaysRemaining(endsAt: String?): Long {
    if (endsAt == null) return 0
    return try {
        val diff = Instant.parse(endsAt).toEpochMilliseconds() - DateTimeUtils.systemCurrentMilliseconds()
        if (diff < 0) 0 else diff / (1000 * 60 * 60 * 24)
    } catch (e: Exception) {
        0
    }
}
