package xyz.sattar.javid.proqueue.core.push

// Web push (Firebase JS SDK + service worker) — docs/OWNER_WEB_PLAN.md
// section 10.1. The actual SDK/service-worker interop lives in
// FirebaseMessagingInterop.kt, which already fails soft on every reason this
// can come back null: no VAPID key configured yet (FirebaseWebConfig), the
// vendored Firebase SDK script missing/failed to load, no service-worker/
// Notification support, or the browser permission not granted. In every one
// of those cases SyncPushTokenUseCase sees exactly what it already handles
// for iOS — "no token" — and the owner still gets every reminder through
// the channels that don't depend on push.
actual object PushTokenProvider {
    actual val platform: String = "WEB"

    actual suspend fun currentToken(): String? = requestFcmWebToken()
}
