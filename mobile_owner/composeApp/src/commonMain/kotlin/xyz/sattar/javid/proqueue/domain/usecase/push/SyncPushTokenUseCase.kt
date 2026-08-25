package xyz.sattar.javid.proqueue.domain.usecase.push

import xyz.sattar.javid.proqueue.core.push.PushTokenProvider
import xyz.sattar.javid.proqueue.core.utils.AppInfo
import xyz.sattar.javid.proqueue.data.remoteDataSource.device.DeviceApiService
import xyz.sattar.javid.proqueue.data.remoteDataSource.device.RegisterDeviceRequestDto

/**
 * Tell the backend which device to push to.
 *
 * Called after a successful login and on every start of an already-signed-in
 * app: FCM rotates tokens on its own schedule (reinstall, restored backup, app
 * data cleared), and a stale token is silently undeliverable, so re-registering
 * the current one at each start is cheaper than detecting the rotation.
 *
 * Fails silently. Push is a convenience channel next to the SMS the client
 * receives, and no screen exists on which "couldn't register for notifications"
 * would be an actionable message.
 */
class SyncPushTokenUseCase(
    private val deviceApiService: DeviceApiService
) {
    suspend operator fun invoke(): Boolean {
        return try {
            val token = PushTokenProvider.currentToken() ?: return false
            deviceApiService.register(
                RegisterDeviceRequestDto(
                    token = token,
                    platform = PushTokenProvider.platform,
                    appVersion = AppInfo.versionName
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
