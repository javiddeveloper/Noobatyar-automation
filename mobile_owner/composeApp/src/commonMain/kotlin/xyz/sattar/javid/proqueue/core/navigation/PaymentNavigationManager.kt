package xyz.sattar.javid.proqueue.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-shot bridge from the platform-level payment-result deep link
 * (`noobatyar://payment/result?...`) into the nav graph — mirrors
 * [NotificationNavigationManager].
 *
 * We deliberately do NOT rely on Navigation-Compose's own `navDeepLink`
 * auto-handling of the Activity's launch [android.content.Intent]: that
 * mechanism re-processes the *same* Intent every time a fresh
 * NavHostController has its graph set (e.g. after MainNavHost is torn down
 * and recreated on a business switch), which was replaying stale payment
 * results. The platform layer parses the URI once, calls [navigate], and
 * clears the Intent's data so it can never be replayed; [consumeEvent] then
 * makes sure this event, too, is only acted on once.
 */
object PaymentNavigationManager {
    private val _paymentEvent = MutableStateFlow<PaymentResultEvent?>(null)
    val paymentEvent = _paymentEvent.asStateFlow()

    fun navigate(event: PaymentResultEvent) {
        _paymentEvent.value = event
    }

    fun consumeEvent() {
        _paymentEvent.value = null
    }
}

data class PaymentResultEvent(
    val success: Boolean,
    val ref: String? = null,
    val amount: String? = null,
    val txn: String? = null
)
