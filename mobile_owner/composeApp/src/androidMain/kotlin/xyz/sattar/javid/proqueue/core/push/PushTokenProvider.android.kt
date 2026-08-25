package xyz.sattar.javid.proqueue.core.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The FCM registration token for this installation.
 *
 * Wrapped by hand rather than through kotlinx-coroutines-play-services so the
 * app doesn't take on that dependency for one call. Every failure path returns
 * null: without google-services.json the Firebase SDK throws on first use, and
 * a build made before push was configured has to keep working.
 */
actual object PushTokenProvider {
    actual val platform: String = "ANDROID"

    actual suspend fun currentToken(): String? = suspendCancellableCoroutine { continuation ->
        try {
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    continuation.resume(
                        if (task.isSuccessful) task.result else null
                    )
                }
        } catch (e: Throwable) {
            continuation.resume(null)
        }
    }
}
