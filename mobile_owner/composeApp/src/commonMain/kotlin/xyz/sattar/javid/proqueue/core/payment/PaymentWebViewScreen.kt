package xyz.sattar.javid.proqueue.core.payment

import androidx.compose.runtime.Composable

/**
 * Hosts the Zibal payment page (`payment_url` from the backend) so the
 * gateway sees a real `Referer` header pointing at our own domain —
 * required by Zibal since ۲۰ شهریور ۱۴۰۵ (see the help article linked from
 * their SMS notice). Bouncing out to the external system browser
 * ([androidx.compose.ui.platform.UriHandler.openUri], the previous
 * behaviour) never carries a Referer at all: there is no "referring page"
 * in that browsing context, since the OS launched a fresh tab rather than
 * navigating from one.
 *
 * [onNavigateBack] fires once the flow is done — either because the WebView
 * detected the `noobatyar://payment/result` callback (Android) or, on
 * platforms that don't yet have a Referer-safe WebView actual, immediately
 * after handing off to the platform's normal external-open behaviour
 * (unchanged from before this screen existed).
 */
@Composable
expect fun PaymentWebViewScreen(url: String, onNavigateBack: () -> Unit)
