package xyz.sattar.javid.proqueue.core.payment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalUriHandler

/**
 * A normal browser navigation (window.location / window.open, whatever
 * LocalUriHandler does on this target) already carries a Referer header by
 * default — same reasoning front_client's plain `window.location.href =`
 * needed no change for. Only the native Android/iOS apps lacked one at all,
 * since an external-app URL launch has no "referring page" to carry it from.
 */
@Composable
actual fun PaymentWebViewScreen(url: String, onNavigateBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(url) {
        uriHandler.openUri(url)
        onNavigateBack()
    }
}
