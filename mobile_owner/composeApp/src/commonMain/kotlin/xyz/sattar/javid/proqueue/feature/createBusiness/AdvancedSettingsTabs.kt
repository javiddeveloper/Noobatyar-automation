package xyz.sattar.javid.proqueue.feature.createBusiness

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.jetbrains.compose.resources.stringResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.coming_soon_badge
import proqueue.composeapp.generated.resources.coming_soon_hint
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField
import xyz.sattar.javid.proqueue.core.ui.components.FeatureGate
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementKeys
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementsResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto

/**
 * The business "advanced settings" area, organized by *topic* rather than by
 * pricing tier: پرداخت (payments) → ظرفیت (capacity & deposit) → یادآوری
 * (reminders & SMS). Within each topic, a capability that the user's plan does
 * not include is replaced inline by an upgrade card naming the cheapest plan
 * that unlocks it — so, e.g., all the ways of receiving money live together and
 * only the premium ones (online gateway) show a lock, instead of card-to-card
 * and the online gateway being split across separate tabs.
 *
 * Feature → capability mapping mirrors backend/accounting/entitlements.py
 * exactly, so nothing shown as "unlocked" here can be rejected by the server.
 */
@Composable
fun AdvancedSettingsTabs(
    entitlements: EntitlementsResponseDto?,
    plans: List<PlanDto>,
    onUpgrade: (Int) -> Unit,
    isLoading: Boolean,
    acceptedPaymentMethods: Set<String>,
    onAcceptedPaymentMethods: (Set<String>) -> Unit,
    cardNumber: String,
    onCardNumber: (String) -> Unit,
    cardOwnerName: String,
    onCardOwnerName: (String) -> Unit,
    maxAppointmentsPerHour: String,
    onMaxAppointmentsPerHour: (String) -> Unit,
    depositMode: String,
    onDepositMode: (String) -> Unit,
    depositAmount: String,
    onDepositAmount: (String) -> Unit,
    merchantId: String,
    onMerchantId: (String) -> Unit,
    paymentLink: String,
    onPaymentLink: (String) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("پرداخت", "ظرفیت و بیعانه", "یادآوری")

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(label, style = MaterialTheme.typography.labelLarge) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> PaymentTab(
                entitlements = entitlements,
                plans = plans,
                onUpgrade = onUpgrade,
                isLoading = isLoading,
                acceptedPaymentMethods = acceptedPaymentMethods,
                onAcceptedPaymentMethods = onAcceptedPaymentMethods,
                cardNumber = cardNumber,
                onCardNumber = onCardNumber,
                cardOwnerName = cardOwnerName,
                onCardOwnerName = onCardOwnerName,
                merchantId = merchantId,
                onMerchantId = onMerchantId,
                paymentLink = paymentLink,
                onPaymentLink = onPaymentLink
            )
            1 -> CapacityTab(
                entitlements = entitlements,
                plans = plans,
                onUpgrade = onUpgrade,
                isLoading = isLoading,
                maxAppointmentsPerHour = maxAppointmentsPerHour,
                onMaxAppointmentsPerHour = onMaxAppointmentsPerHour,
                depositMode = depositMode,
                onDepositMode = onDepositMode,
                depositAmount = depositAmount,
                onDepositAmount = onDepositAmount
            )
            2 -> RemindersTab(entitlements = entitlements, plans = plans, onUpgrade = onUpgrade)
        }
    }
}

/**
 * All the ways of receiving money in one place. Only cash (pay in person) is
 * free on every plan; card-to-card and the online bank gateway are premium
 * capabilities and are gated inline (card-to-card lives in the deposit tier).
 */
@Composable
private fun PaymentTab(
    entitlements: EntitlementsResponseDto?,
    plans: List<PlanDto>,
    onUpgrade: (Int) -> Unit,
    isLoading: Boolean,
    acceptedPaymentMethods: Set<String>,
    onAcceptedPaymentMethods: (Set<String>) -> Unit,
    cardNumber: String,
    onCardNumber: (String) -> Unit,
    cardOwnerName: String,
    onCardOwnerName: (String) -> Unit,
    merchantId: String,
    onMerchantId: (String) -> Unit,
    paymentLink: String,
    onPaymentLink: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "روش‌های دریافت وجه را انتخاب کنید.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // پرداخت در محل — رایگان در همه‌ی پلن‌ها
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = acceptedPaymentMethods.contains("CASH"),
                onCheckedChange = { checked ->
                    val newSet = acceptedPaymentMethods.toMutableSet()
                    if (checked) newSet.add("CASH") else newSet.remove("CASH")
                    onAcceptedPaymentMethods(newSet)
                }
            )
            Text("پرداخت در محل", style = MaterialTheme.typography.bodyMedium)
        }

        // کارت به کارت — قابلیت پلن تعهد/بیعانه (به‌صورت درجا قفل می‌شود)
        FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.DEPOSIT,
            title = "کارت به کارت",
            description = "دریافت مبلغ نوبت با انتقال کارت‌به‌کارت.",
            onUpgrade = onUpgrade
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = acceptedPaymentMethods.contains("CARD"),
                        onCheckedChange = { checked ->
                            val newSet = acceptedPaymentMethods.toMutableSet()
                            if (checked) newSet.add("CARD") else newSet.remove("CARD")
                            onAcceptedPaymentMethods(newSet)
                        }
                    )
                    Text("کارت به کارت", style = MaterialTheme.typography.bodyMedium)
                }
                if (acceptedPaymentMethods.contains("CARD")) {
                    AppTextField(
                        enabled = !isLoading,
                        value = cardNumber,
                        onValueChange = onCardNumber,
                        label = "شماره کارت (۱۶ رقم)",
                        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, bottom = 8.dp),
                        keyboardType = KeyboardType.Number,
                        maxLength = 16,
                        visualTransformation = xyz.sattar.javid.proqueue.core.ui.utils.CardNumberVisualTransformation()
                    )
                    AppTextField(
                        enabled = !isLoading,
                        value = cardOwnerName,
                        onValueChange = onCardOwnerName,
                        label = "نام صاحب کارت",
                        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, bottom = 8.dp),
                        keyboardType = KeyboardType.Text
                    )
                }
            }
        }

        // درگاه پرداخت آنلاین — قابلیت پلن‌های ویژه (به‌صورت درجا قفل می‌شود)
        FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.ONLINE_GATEWAY,
            title = "درگاه پرداخت آنلاین",
            description = "دریافت مبلغ نوبت مستقیم از طریق درگاه بانکی.",
            onUpgrade = onUpgrade
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = acceptedPaymentMethods.contains("ONLINE"),
                        onCheckedChange = { checked ->
                            val newSet = acceptedPaymentMethods.toMutableSet()
                            if (checked) newSet.add("ONLINE") else newSet.remove("ONLINE")
                            onAcceptedPaymentMethods(newSet)
                        }
                    )
                    Text("فعال‌سازی درگاه آنلاین", style = MaterialTheme.typography.bodyMedium)
                }
                if (acceptedPaymentMethods.contains("ONLINE")) {
                    AppTextField(
                        enabled = !isLoading,
                        value = merchantId,
                        onValueChange = onMerchantId,
                        label = "مرچنت آیدی زیبال (Merchant ID)",
                        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, bottom = 4.dp),
                        keyboardType = KeyboardType.Text
                    )
                    Text(
                        text = "با پر کردن این فیلد، مشتری مستقیم به درگاه بانکی می‌رود و " +
                                "بیعانه به حساب زیبالِ شما واریز می‌شود. نوبت بلافاصله قطعی " +
                                "می‌شود و نیازی به بررسی فیش نیست. مرچنت آیدی را از پنل " +
                                "zibal.ir بردارید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp, bottom = 12.dp)
                    )
                    AppTextField(
                        enabled = !isLoading,
                        value = paymentLink,
                        onValueChange = onPaymentLink,
                        label = "لینک پرداخت (در نبودِ مرچنت آیدی)",
                        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, bottom = 4.dp),
                        keyboardType = KeyboardType.Uri
                    )
                    Text(
                        text = "فقط وقتی لازم است که حساب زیبال ندارید. مشتری به این لینک " +
                                "می‌رود، پرداخت می‌کند و بعد باید شماره پیگیری را دستی وارد " +
                                "کند تا شما تأیید کنید. چون مبلغ به لینک فرستاده نمی‌شود، " +
                                "لینک باید مبلغِ ثابتِ برابر با بیعانه داشته باشد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Deposit modes as the backend defines them (Business.DEPOSIT_MODE_CHOICES).
 * OPTIONAL is a real third state: the client may pay the deposit or choose to
 * settle in person, so it must not collapse into a boolean.
 */
enum class DepositMode(val value: String, val label: String) {
    NONE("NONE", "بدون بیعانه"),
    OPTIONAL("OPTIONAL", "اختیاری"),
    MANDATORY("MANDATORY", "اجباری");

    companion object {
        fun describe(value: String): String = when (value) {
            OPTIONAL.value -> "مشتری می‌تواند بیعانه را پرداخت کند یا «پرداخت در محل» را انتخاب کند."
            MANDATORY.value -> "مشتری برای نهایی شدن نوبت باید بیعانه را پرداخت کند."
            else -> "نوبت بدون دریافت بیعانه ثبت می‌شود."
        }
    }
}

/** Capacity control and deposit — both gated capabilities, grouped together. */
@Composable
private fun CapacityTab(
    entitlements: EntitlementsResponseDto?,
    plans: List<PlanDto>,
    onUpgrade: (Int) -> Unit,
    isLoading: Boolean,
    maxAppointmentsPerHour: String,
    onMaxAppointmentsPerHour: (String) -> Unit,
    depositMode: String,
    onDepositMode: (String) -> Unit,
    depositAmount: String,
    onDepositAmount: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.CAPACITY_CONTROL,
            title = "کنترل ظرفیت ساعتی",
            description = "محدود کردن حداکثر تعداد نوبت در هر ساعت.",
            onUpgrade = onUpgrade
        ) {
            AppTextField(
                enabled = !isLoading,
                value = maxAppointmentsPerHour,
                onValueChange = onMaxAppointmentsPerHour,
                label = "حداکثر نوبت در هر ساعت (خالی = نامحدود)",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Number
            )
        }

        FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.DEPOSIT,
            title = "دریافت بیعانه",
            description = "از مشتری هنگام رزرو بیعانه دریافت کنید.",
            onUpgrade = onUpgrade
        ) {
            Column {
                Text("دریافت بیعانه", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                // Three real modes, not a switch: OPTIONAL is a distinct
                // behaviour (client may pay the deposit or choose to pay in
                // person), and a boolean silently rewrote it to MANDATORY.
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    DepositMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = depositMode == mode.value,
                            onClick = { onDepositMode(mode.value) },
                            enabled = !isLoading,
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = DepositMode.entries.size
                            )
                        ) {
                            Text(mode.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = DepositMode.describe(depositMode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (depositMode != DepositMode.NONE.value) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AppTextField(
                        enabled = !isLoading,
                        value = depositAmount,
                        onValueChange = onDepositAmount,
                        label = "مبلغ بیعانه (تومان)",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardType = KeyboardType.Number,
                        visualTransformation = xyz.sattar.javid.proqueue.core.ui.utils.CurrencyVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // The deposit amount lives here but the *method* is chosen in
                    // the «پرداخت» tab. Without saying so, the two screens look
                    // unrelated and it is not obvious how the money arrives.
                    Text(
                        text = "بیعانه از همان روش‌هایی گرفته می‌شود که در تب «پرداخت» " +
                                "فعال کرده‌اید — کارت به کارت یا درگاه آنلاین.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Messaging capabilities — promotional SMS and multi-channel reminders. */
@Composable
private fun RemindersTab(
    entitlements: EntitlementsResponseDto?,
    plans: List<PlanDto>,
    onUpgrade: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.PROMOTIONAL_SMS,
            title = "پیامک تبلیغاتی",
            description = "ارسال پیامک کمپین و تبلیغات به مشتریان.",
            onUpgrade = onUpgrade
        ) {
            IncludedFeatureRow("امکان ارسال پیامک تبلیغاتی برای این کسب‌وکار فعال است.")
        }

        FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.MULTI_CHANNEL,
            title = "یادآوری چندکاناله",
            description = "یادآوری خودکار نوبت از طریق واتساپ و تلگرام، علاوه بر پیامک.",
            onUpgrade = onUpgrade
        ) {
            IncludedFeatureRow("یادآوری خودکار از طریق پیامک، واتساپ و تلگرام برای این کسب‌وکار فعال است.")
        }
    }
}

@Composable
private fun IncludedFeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
