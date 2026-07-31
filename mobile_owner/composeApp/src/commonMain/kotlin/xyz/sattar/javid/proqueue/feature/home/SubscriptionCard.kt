package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    val planName = plan?.name ?: ""
    val isTrial = planName.contains("آزمایشی")

    // رنگ‌بندی متناسب با نام پلن
    val (gradientColors, icon, badgeText) = when {
        !isValid -> Triple(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant
            ),
            Icons.Rounded.Info,
            "بدون اشتراک"
        )
        planName.contains("پرو پلاس") -> Triple(
            listOf(Color(0xFF4A148C), Color(0xFF7B1FA2)),
            Icons.Rounded.WorkspacePremium,
            "💎 پرو پلاس"
        )
        planName.contains("پرو") -> Triple(
            listOf(Color(0xFF1A237E), Color(0xFF283593)),
            Icons.Rounded.AutoAwesome,
            "🚀 پرو"
        )
        planName.contains("اکو") -> Triple(
            listOf(Color(0xFF1B5E20), Color(0xFF2E7D32)),
            Icons.Rounded.Spa,
            "🌿 اکو"
        )
        planName.contains("پایه") -> Triple(
            listOf(Color(0xFF0D47A1), Color(0xFF1565C0)),
            Icons.Rounded.FlashOn,
            "⚡ پایه"
        )
        isTrial -> Triple(
            listOf(Color(0xFF37474F), Color(0xFF546E7A)),
            Icons.Rounded.Stars,
            "🌱 آزمایشی"
        )
        else -> Triple(
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
            Icons.Rounded.Star,
            planName
        )
    }

    val daysRemaining = calculateDaysRemaining(subscription?.endsAt)
    val isExpiringSoon = daysRemaining in 1..7

    if (isValid) {
        // کارت اشتراک فعال — گرادینت رنگی
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(gradientColors))
                    .padding(16.dp)
            ) {
                // آیکون تزئینی پس‌زمینه
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(90.dp)
                        .align(Alignment.CenterEnd)
                        .offset(x = 20.dp),
                    tint = Color.White.copy(alpha = 0.08f)
                )

                Column {
                    // Badge نام پلن
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = badgeText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // نشانگر روز باقی‌مانده
                        Surface(
                            color = if (isExpiringSoon)
                                Color(0xFFFF6F00).copy(alpha = 0.85f)
                            else
                                Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Timer,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "$daysRemaining روز",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SubscriptionInfoItem(
                            label = "شروع",
                            value = formatIsoDate(subscription?.startedAt),
                            textColor = Color.White
                        )
                        SubscriptionInfoItem(
                            label = "پایان",
                            value = formatIsoDate(subscription?.endsAt),
                            textColor = if (isExpiringSoon) Color(0xFFFFCC80) else Color.White
                        )
                    }

                    if (isExpiringSoon) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFFFF6F00).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Warning,
                                    null,
                                    tint = Color(0xFFFFCC80),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "اشتراک شما به زودی منقضی می‌شود",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFFCC80)
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // کارت بدون اشتراک
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(20.dp),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "فاقد اشتراک فعال",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "برای استفاده از امکانات نوبت‌یار اشتراک تهیه کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionInfoItem(label: String, value: String, textColor: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.65f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
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
