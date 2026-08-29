package xyz.sattar.javid.proqueue.core.push

import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.suspendCancellableCoroutine

// Must match the SW registration in index.html's expectations and the
// firebase-messaging-sw.js file living at the site root — see that file's
// header comment for why the path can't just be relative.
private const val SERVICE_WORKER_PATH = "/firebase-messaging-sw.js"

/**
 * Everything this file does happens inside one JS snippet rather than a web
 * of small `external` bindings, on purpose: `getToken`/`initializeApp`/
 * `onMessage` are all Promise- and dictionary-object-heavy, which is exactly
 * the kind of shape `@JsFun` exists for (wasmJs has no `js("...")` escape
 * hatch — see core/utils/ContactActions.wasmJs.kt's comment — and hand-built
 * `external interface`s for a whole Promise-chaining SDK would be a lot of
 * surface for something that only needs to report back a token or "no").
 *
 * The whole thing already fails soft in JS, before any Kotlin sees it:
 * `firebase` undefined (the vendored SDK files — see index.html — missing
 * or failed to parse), `serviceWorker` unsupported, `Notification` unsupported,
 * `getToken` rejecting (permission denied/blocked, bad VAPID key, network) —
 * every one of those calls `onError` instead of throwing across the wasm/JS boundary,
 * because an uncaught JS exception there does not become a catchable Kotlin
 * exception.
 *
 * This is also where the foreground case from the task's point 5 lives:
 * `messaging.onMessage` is registered right after `messaging` is obtained
 * (not gated behind a successful `getToken`), so a message that arrives
 * while this tab is focused shows a `Notification` here instead of being
 * silently dropped — see firebase-messaging-sw.js's header for why the
 * service worker alone can't cover that case.
 */
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (apiKey, authDomain, projectId, storageBucket, messagingSenderId, appId, vapidKey, swPath, onToken, onError) => {
        try {
            if (typeof firebase === 'undefined') { onError('firebase SDK script did not load'); return; }
            const app = (firebase.apps && firebase.apps.length)
                ? firebase.apps[0]
                : firebase.initializeApp({ apiKey, authDomain, projectId, storageBucket, messagingSenderId, appId });
            const messaging = firebase.messaging(app);

            // Foreground handling (task point 5): the SDK only invokes the
            // service worker's background handler while no client page for
            // this app is focused, so a message that arrives with the tab
            // open has to be shown from here instead.
            try {
                messaging.onMessage((payload) => {
                    try {
                        const n = payload && payload.notification;
                        const title = (n && n.title) || 'نوبت‌یار';
                        const body = (n && n.body) || '';
                        if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
                            new Notification(title, { body: body });
                        }
                    } catch (e) {
                        // Never let a malformed/unexpected payload break the
                        // listener itself.
                    }
                });
            } catch (e) {
                // messaging.onMessage not available in this browser — the
                // background/service-worker path still covers pushes.
            }

            const requestToken = (swRegistration) => {
                messaging.getToken({ vapidKey: vapidKey, serviceWorkerRegistration: swRegistration || undefined })
                    .then((token) => onToken(token || ''))
                    .catch((e) => onError(String(e)));
            };

            if ('serviceWorker' in navigator) {
                navigator.serviceWorker.register(swPath)
                    .then(requestToken)
                    // A failed *registration* still lets getToken try its own
                    // default lookup of /firebase-messaging-sw.js.
                    .catch(() => requestToken(null));
            } else {
                requestToken(null);
            }
        } catch (e) {
            onError(String(e));
        }
    }
    """
)
private external fun jsRequestFcmToken(
    apiKey: String,
    authDomain: String,
    projectId: String,
    storageBucket: String,
    messagingSenderId: String,
    appId: String,
    vapidKey: String,
    swPath: String,
    onToken: (String) -> Unit,
    onError: (String) -> Unit
)

/**
 * Resolves the FCM web token, or null if push isn't usable for any reason.
 * See the class doc above and [FirebaseWebConfig.isConfigured] for the full
 * list of things this treats as "no push" rather than an error to surface.
 */
internal suspend fun requestFcmWebToken(): String? {
    if (!FirebaseWebConfig.isConfigured) return null
    return try {
        suspendCancellableCoroutine { cont ->
            jsRequestFcmToken(
                apiKey = FirebaseWebConfig.API_KEY,
                authDomain = FirebaseWebConfig.AUTH_DOMAIN,
                projectId = FirebaseWebConfig.PROJECT_ID,
                storageBucket = FirebaseWebConfig.STORAGE_BUCKET,
                messagingSenderId = FirebaseWebConfig.MESSAGING_SENDER_ID,
                appId = FirebaseWebConfig.WEB_APP_ID,
                vapidKey = FirebaseWebConfig.FCM_WEB_VAPID_KEY,
                swPath = SERVICE_WORKER_PATH,
                onToken = { token -> if (cont.isActive) cont.resume(token.ifBlank { null }) { } },
                onError = { _ -> if (cont.isActive) cont.resume(null) { } }
            )
        }
    } catch (e: Throwable) {
        null
    }
}

/**
 * Asks the browser for notification permission. Must be called from a real
 * user gesture's call stack (a click handler) — browsers reject or silently
 * ignore `Notification.requestPermission()` calls that aren't, which is
 * exactly the situation `NotificationPermissionLauncher.wasmJs.kt` is
 * called from (`NotificationsScreen`'s toggle).
 */
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (onResult) => {
        try {
            if (typeof Notification === 'undefined') { onResult(false); return; }
            if (Notification.permission === 'granted') { onResult(true); return; }
            if (Notification.permission === 'denied') { onResult(false); return; }
            Notification.requestPermission()
                .then((permission) => onResult(permission === 'granted'))
                .catch(() => onResult(false));
        } catch (e) {
            onResult(false);
        }
    }
    """
)
internal external fun jsRequestNotificationPermission(onResult: (Boolean) -> Unit)
