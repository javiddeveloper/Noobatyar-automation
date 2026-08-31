package xyz.sattar.javid.proqueue.core.payment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.Foundation.NSError
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorFailingURLStringErrorKey
import platform.Foundation.setValue
import platform.UIKit.UIView
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.darwin.NSObject
import xyz.sattar.javid.proqueue.core.navigation.PaymentNavigationManager
import xyz.sattar.javid.proqueue.core.navigation.PaymentResultEvent

/** Must match the Android actual's GATEWAY_REFERER — same registered domain. */
private const val GATEWAY_REFERER = "https://app.noobatyar.ir/"

/**
 * iOS actual, mirroring the Android WebView (see that file's doc for why an
 * in-app WebView is needed at all — Zibal's Referer requirement).
 *
 * UNVERIFIED ON A REAL DEVICE — written from documentation/research only,
 * with no Mac/Xcode toolchain available to compile or run this on iOS in
 * this environment. Two things specifically deserve a real device test
 * before this ships:
 *
 * 1. `androidx.compose.ui.viewinterop.UIKitView` is the current package per
 *    Kotlin's own docs (moved from `androidx.compose.ui.interop` in CMP
 *    1.7, and this project is on 1.11.1) — but that move was confirmed
 *    from documentation, not this project's own compiler.
 * 2. The callback is caught via `didFailProvisionalNavigation`, NOT
 *    `decidePolicyForNavigationAction`. That's deliberate: WKWebView has no
 *    handler for the custom `noobatyar://` scheme, so it fails the
 *    navigation on its own — no interception needed — and
 *    JetBrains/compose-multiplatform#3843 documents `decidePolicyFor
 *    NavigationAction`'s completion-handler variant rendering a blank page
 *    on iOS in this exact interop layer. `didFailProvisionalNavigation`
 *    takes no completion handler, so it should sidestep that bug rather
 *    than risk hitting it.
 */
@Composable
actual fun PaymentWebViewScreen(url: String, onNavigateBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    val currentOnDone = rememberUpdatedState(onNavigateBack)

    // `WKWebView.navigationDelegate` is a *weak* reference on the ObjC side —
    // a delegate object with no other owner would be free to get collected
    // out from under it. `remember` gives it a strong owner for exactly this
    // composable's lifetime, the same fix the reference implementation this
    // was modeled on uses.
    val delegate = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                loading = false
            }

            // WKWebView never even attempts our custom scheme as a real page
            // load — it fails the navigation on its own, which is exactly
            // what lands here. Deliberately not using
            // decidePolicyForNavigationAction's completion-handler overload
            // to catch this instead: JetBrains/compose-multiplatform#3843
            // documents that exact overload rendering a blank page in this
            // interop layer. This method takes no completion handler.
            override fun webView(
                webView: WKWebView,
                didFailProvisionalNavigation: WKNavigation?,
                withError: NSError
            ) {
                loading = false
                val failingUrl =
                    withError.userInfo[NSURLErrorFailingURLStringErrorKey] as? String
                if (failingUrl?.startsWith("noobatyar://payment/result") == true) {
                    val uri = NSURL(string = failingUrl)
                    fun param(name: String): String? =
                        uri.query
                            ?.split("&")
                            ?.map { it.split("=", limit = 2) }
                            ?.firstOrNull { it.getOrNull(0) == name }
                            ?.getOrNull(1)

                    PaymentNavigationManager.navigate(
                        PaymentResultEvent(
                            success = param("success") == "1",
                            ref = param("ref"),
                            amount = param("amount"),
                            txn = param("txn")
                        )
                    )
                    currentOnDone.value()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        UIKitView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                val webView = WKWebView()
                webView.navigationDelegate = delegate
                val request = NSMutableURLRequest(uRL = NSURL(string = url)!!)
                request.setValue(GATEWAY_REFERER, forHTTPHeaderField = "Referer")
                webView.loadRequest(request)
                webView
            }
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
