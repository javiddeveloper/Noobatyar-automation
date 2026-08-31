package xyz.sattar.javid.proqueue.core.push

/**
 * The device's push registration token, as issued by the platform's push service.
 *
 * Android returns an FCM token. iOS returns null until APNs is configured for
 * this app — the owner still gets every reminder by the channels that don't
 * depend on push, so a null here degrades the experience rather than breaking it.
 */
expect object PushTokenProvider {
    /** "ANDROID" / "IOS", matching the backend's DeviceToken.PLATFORM_CHOICES. */
    val platform: String

    /** Null when push isn't available on this build (no Firebase config, iOS). */
    suspend fun currentToken(): String?
}
