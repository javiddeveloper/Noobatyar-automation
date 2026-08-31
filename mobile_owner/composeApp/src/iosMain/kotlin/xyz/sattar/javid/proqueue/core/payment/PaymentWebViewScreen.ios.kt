package xyz.sattar.javid.proqueue.core.payment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalUriHandler

/**
 * Android got the real Referer-safe in-app WebView first (see the .android.kt
 * actual's doc); iOS still bounces out to the external browser exactly as it
 * did before this screen existed, so nothing regresses while that follow-up
 * is pending. TODO: an SFSafariViewController/WKWebView actual with the same
 * Referer header injection and noobatyar://payment/result interception.
 */
@Composable
actual fun PaymentWebViewScreen(url: String, onNavigateBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(url) {
        uriHandler.openUri(url)
        onNavigateBack()
    }
}
