package xyz.sattar.javid.proqueue

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

import android.content.Intent
import xyz.sattar.javid.proqueue.core.navigation.NotificationNavigationManager
import xyz.sattar.javid.proqueue.core.navigation.NavigationEvent
import xyz.sattar.javid.proqueue.core.navigation.PaymentNavigationManager
import xyz.sattar.javid.proqueue.core.navigation.PaymentResultEvent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleNotificationIntent(intent)
        handlePaymentDeepLink(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
        handlePaymentDeepLink(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("from_notification", false) == true) {
            val businessId = intent.getLongExtra("businessId", -1)
            val visitorId = intent.getLongExtra("visitorId", -1)
            val openMessageDialog = intent.getBooleanExtra("openMessageDialog", false)

            if (visitorId != -1L) {
                NotificationNavigationManager.navigate(
                    NavigationEvent.ToVisitorDetails(
                        visitorId = visitorId,
                        openMessageDialog = openMessageDialog
                    )
                )
            }
        }
    }

    /**
     * Parses the `noobatyar://payment/result?...` deep link ourselves instead
     * of leaning on Navigation-Compose's built-in `navDeepLink` handling of
     * this Activity's intent. That built-in mechanism re-checks the *same*
     * Intent every time a NavHostController's graph is (re)set — which
     * happens on every business switch, since switching businesses tears
     * down and recreates MainNavHost — so a payment result that already
     * happened was silently replaying as a dialog. We handle it once here
     * and clear `intent.data`, so no later NavHost recreation can see it
     * again.
     */
    private fun handlePaymentDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "noobatyar" && uri.host == "payment" && uri.path == "/result") {
            PaymentNavigationManager.navigate(
                PaymentResultEvent(
                    success = uri.getQueryParameter("success") == "1",
                    ref = uri.getQueryParameter("ref"),
                    amount = uri.getQueryParameter("amount"),
                    txn = uri.getQueryParameter("txn")
                )
            )
            intent.data = null
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}