package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.core.network.TokenManager

class ClearTokenUseCase {
    operator fun invoke() {
        TokenManager.clearTokens()
    }
}
