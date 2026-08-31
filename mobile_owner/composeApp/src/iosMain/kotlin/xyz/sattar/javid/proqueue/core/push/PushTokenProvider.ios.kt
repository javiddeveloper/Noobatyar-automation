package xyz.sattar.javid.proqueue.core.push

/**
 * iOS has no push token yet: FCM on iOS rides on APNs, which needs an Apple
 * Developer push key, an entitlement and the Firebase iOS SDK linked into the
 * Xcode project — none of which this build has. Returning null keeps the shared
 * registration path compiling and simply registers no device, rather than
 * pretending a token exists.
 */
actual object PushTokenProvider {
    actual val platform: String = "IOS"
    actual suspend fun currentToken(): String? = null
}
