package xyz.sattar.javid.proqueue.core.push

// Web push (Firebase JS SDK + service worker) is a separate phase —
// docs/OWNER_WEB_PLAN.md section 10.1. Until then this always returns null,
// same as the iOS actual before APNs was configured: SyncPushTokenUseCase
// simply has nothing to register, and the owner still gets every reminder
// through the channels that don't depend on push.
actual object PushTokenProvider {
    actual val platform: String = "WEB"

    actual suspend fun currentToken(): String? = null
}
