package xyz.sattar.javid.proqueue.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.sattar.javid.proqueue.core.ui.components.AppButton

@Composable
fun PaymentResultScreen(
    success: Boolean,
    ref: String?,
    amount: String?,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Handle
                Box(
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                )

                // Icon
                val iconBackground = if (success) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                val iconTint = if (success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                val icon = if (success) Icons.Rounded.Check else Icons.Rounded.Close

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(iconBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (success) "پرداخت موفقیت‌آمیز" else "خطا در پرداخت",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (success)
                        "اشتراک شما با موفقیت فعال شد. اکنون می‌توانید از تمامی امکانات نوبت یار استفاده کنید."
                    else
                        "متأسفانه فرآیند پرداخت با خطا مواجه شد. اگر مبلغی از حساب شما کسر شده، طی ۷۲ ساعت آینده بازگشت داده می‌شود.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                if (success && (!ref.isNullOrBlank() || !amount.isNullOrBlank())) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (!ref.isNullOrBlank()) {
                                ResultRow(label = "کد پیگیری:", value = ref)
                            }
                            if (!amount.isNullOrBlank()) {
                                ResultRow(label = "مبلغ پرداختی:", value = "$amount تومان")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                AppButton(
                    text = if (success) "بسیار عالی" else "فهمیدم",
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
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
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
