package xyz.sattar.javid.proqueue.domain.usecase.push

import xyz.sattar.javid.proqueue.core.push.PushTokenProvider
import xyz.sattar.javid.proqueue.data.remoteDataSource.device.DeviceApiService
import xyz.sattar.javid.proqueue.data.remoteDataSource.device.UnregisterDeviceRequestDto

/**
 * Stop pushing to this device. Called on logout, before the token is cleared —
 * otherwise the next owner to sign in on this phone would keep receiving the
 * previous one's appointments until FCM happened to rotate the token.
 */
class UnregisterPushTokenUseCase(
    private val deviceApiService: DeviceApiService
) {
    suspend operator fun invoke() {
        try {
            val token = PushTokenProvider.currentToken() ?: return
            deviceApiService.unregister(UnregisterDeviceRequestDto(token))
        } catch (e: Exception) {
            // Best effort; the server also retires tokens FCM reports as dead.
        }
    }
}
