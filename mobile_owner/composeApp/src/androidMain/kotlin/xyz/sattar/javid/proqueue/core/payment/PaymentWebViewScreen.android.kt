package xyz.sattar.javid.proqueue.core.payment

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import xyz.sattar.javid.proqueue.core.navigation.PaymentNavigationManager
import xyz.sattar.javid.proqueue.core.navigation.PaymentResultEvent

/**
 * Same domain callbackUrl already points at (backend's `_client_callback` /
 * `CLIENT_WEB_URL`, DEPLOYMENT.md) — must match whatever Zibal has the
 * merchant registered under, or the Referer check fails the same way a
 * missing header does.
 */
private const val GATEWAY_REFERER = "https://app.noobatyar.ir/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PaymentWebViewScreen(url: String, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // rememberUpdatedState so the WebViewClient (built once, inside the
    // AndroidView factory) always calls the *current* onNavigateBack even if
    // recomposition swapped in a new lambda instance.
    val currentOnDone = rememberUpdatedState(onNavigateBack)

    BackHandler {
        val wv = webViewRef.value
        if (wv != null && wv.canGoBack()) wv.goBack() else onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پرداخت") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                super.onPageFinished(view, pageUrl)
                                loading = false
                                canGoBack = view?.canGoBack() == true
                            }

                            /**
                             * The custom-scheme payment-result callback
                             * (see PaymentNavigationManager's doc) and, on
                             * some banks' one-tap flow, an app-switch link
                             * (bank://…, intent://…) never resolve as a
                             * normal page load — a WebView has no default
                             * handler for a scheme it doesn't own. Both are
                             * caught here instead of letting the WebView try
                             * and silently fail.
                             */
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): Boolean {
                                val requestUri = request?.url ?: return false
                                if (requestUri.scheme == "http" || requestUri.scheme == "https") {
                                    return false // let the WebView load it normally
                                }

                                if (requestUri.scheme == "noobatyar" &&
                                    requestUri.host == "payment" &&
                                    requestUri.path == "/result"
                                ) {
                                    PaymentNavigationManager.navigate(
                                        PaymentResultEvent(
                                            success = requestUri.getQueryParameter("success") == "1",
                                            ref = requestUri.getQueryParameter("ref"),
                                            amount = requestUri.getQueryParameter("amount"),
                                            txn = requestUri.getQueryParameter("txn")
                                        )
                                    )
                                    currentOnDone.value()
                                    return true
                                }

                                // Some banks hand off to their own app for
                                // one-tap payment. Best-effort: let the OS
                                // resolve it: if nothing can, the WebView
                                // stays on the gateway page rather than
                                // crashing.
                                return try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, requestUri))
                                    true
                                } catch (e: ActivityNotFoundException) {
                                    false
                                }
                            }
                        }
                        webViewRef.value = this
                        loadUrl(url, mapOf("Referer" to GATEWAY_REFERER))
                    }
                }
            )

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
