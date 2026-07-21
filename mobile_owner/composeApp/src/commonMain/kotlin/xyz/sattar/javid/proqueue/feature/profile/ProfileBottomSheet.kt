package xyz.sattar.javid.proqueue.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SubscriptionDto
import xyz.sattar.javid.proqueue.domain.model.business.Business
import kotlin.time.ExperimentalTime
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.Share
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.buildAnnotatedString
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBottomSheet(
    userName: String?,
    userEmail: String?,
    subscription: SubscriptionDto?,
    business: Business?,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (business != null && !business.logoPath.isNullOrEmpty()) {
                    val url = if (business.logoPath.startsWith("http")) business.logoPath else "${xyz.sattar.javid.proqueue.BuildKonfig.BASE_URL}${business.logoPath}"
                    AsyncImage(
                        model = url,
                        contentDescription = "Business Logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val displayChar = business?.title?.firstOrNull()?.uppercaseChar()?.toString()
                        ?: userName?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                        
                    Text(
                        text = displayChar,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Business/User Name
            Text(
                text = business?.title ?: userName ?: "کاربر نوبت یار",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Category/Services
            if (business != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = business.category.persianName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // User Phone / Bio
            if (business?.bio?.isNotBlank() == true) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = business.bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else if (userEmail != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = userEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (business != null && business.uniqueCode != null) {
                Spacer(modifier = Modifier.height(16.dp))
                val clipboardManager = LocalClipboardManager.current
                val bookingLink = "${xyz.sattar.javid.proqueue.BuildKonfig.BOOKING_BASE_URL}/b/${business.uniqueCode}"
                
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(buildAnnotatedString { append(bookingLink) })
                        // Optionally show a toast here
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("کپی لینک دریافت نوبت")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Subscription Section
            SubscriptionCard(subscription)

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(24.dp))

            // Logout Button
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onLogout),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "خروج از حساب کاربری",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionCard(subscription: SubscriptionDto?) {
    val isValid = subscription?.isValid ?: false
    val plan = subscription?.plan
    val isVip = plan?.isVip ?: false
    val isTrial = plan?.name?.contains("آزمایشی") == true
    val isDark = !MaterialTheme.colorScheme.surface.let { color -> 
        // Simple luminance check to see if we are in dark mode
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
        colors = CardDefaults.cardColors(
            containerColor = cardColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(16.dp),
        border = borderColor?.let { CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(it)) }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
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
                
                // Started At
                SubscriptionInfoRow(
                    label = "تاریخ شروع:",
                    value = formatIsoDate(subscription.startedAt)
                )
                
                // Ends At
                SubscriptionInfoRow(
                    label = "تاریخ انقضا:",
                    value = formatIsoDate(subscription.endsAt)
                )

                // Days Remaining
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@OptIn(ExperimentalTime::class)
private fun formatIsoDate(isoString: String?): String {
    if (isoString == null) return "--"
    return try {
        val instant = Instant.parse(isoString)
        DateTimeUtils.formatDate(instant.toEpochMilliseconds())
    } catch (e: Exception) {
        "--"
    }
}

@OptIn(ExperimentalTime::class)
private fun calculateDaysRemaining(endsAt: String?): Long {
    if (endsAt == null) return 0
    return try {
        val endInstant = Instant.parse(endsAt)
        val now = DateTimeUtils.systemCurrentMilliseconds()
        val diff = endInstant.toEpochMilliseconds() - now
        if (diff < 0) 0 else diff / (1000 * 60 * 60 * 24)
    } catch (e: Exception) {
        0
    }
}
