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
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.ui.components.AppButton

/**
 * Picks the layout by window width. Compact keeps the existing phone screen
 * (a bottom sheet over a dim scrim) untouched; Medium/Expanded get a
 * properly centred card instead — see [PaymentResultWebContent]. This screen
 * only reports the outcome of an already-completed Zibal transaction, read
 * from [success]/[ref]/[amount] exactly as before in both layouts — only
 * where that result sits on screen changes.
 */
@Composable
fun PaymentResultScreen(
    success: Boolean,
    ref: String?,
    amount: String?,
    onDone: () -> Unit
) {
    if (LocalWindowSize.current == WindowSize.Compact) {
        PaymentResultPhoneContent(success, ref, amount, onDone)
    } else {
        PaymentResultWebContent(success, ref, amount, onDone)
    }
}

/** Phone layout — unchanged. A bottom sheet pinned to the bottom edge of the
 *  viewport over a dim scrim, which is the right pattern for a phone modal.
 *  See [PaymentResultWebContent] for the desktop case, where the same sheet
 *  pinned to the bottom of a mostly-empty browser window is the "stretched
 *  phone app" tell this whole re-layout exists to fix. */
@Composable
private fun PaymentResultPhoneContent(
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

                PaymentResultBody(success = success, ref = ref, amount = amount)

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

/**
 * Desktop layout: the same status card, but centred in the viewport instead
 * of welded to the bottom edge. A bottom sheet makes sense on a phone (thumb
 * reach, native affordance); on a wide browser window it just leaves the
 * action floating at the bottom of an otherwise empty page, which is exactly
 * the "stretched phone app" tell this screen is called out for. Width-capped
 * to [ContentWidthDialog] (narrower than the shared [ContentWidth.Form]) so a
 * one-line status message doesn't stretch into a wide, sparse card either.
 */
@Composable
private fun PaymentResultWebContent(
    success: Boolean,
    ref: String?,
    amount: String?,
    onDone: () -> Unit
) {
    // fillMaxSize is load-bearing here too (see AppScaffold's own comment):
    // without it this Box only wraps the capped Surface below, so centering
    // has nothing to centre within.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = ContentWidthDialog),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PaymentResultBody(success = success, ref = ref, amount = amount)

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

/** Narrower than [xyz.sattar.javid.proqueue.core.ui.components.ContentWidth.Form]:
 *  this is a status message and one button, not a multi-field form, so the
 *  420dp form cap still reads as unnecessarily wide for it. */
private val ContentWidthDialog = 380.dp

/** Icon, headline, message and (on success) the ref/amount summary — the
 *  part of the sheet/card that's identical between phone and desktop.
 *  Extracted so the two layouts can't drift on how the payment result itself
 *  is read or displayed. */
@Composable
private fun PaymentResultBody(
    success: Boolean,
    ref: String?,
    amount: String?
) {
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
