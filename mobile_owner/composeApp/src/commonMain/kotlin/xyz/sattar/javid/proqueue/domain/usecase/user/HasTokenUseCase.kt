package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.core.network.TokenManager

class HasTokenUseCase {
    operator fun invoke(): Boolean = TokenManager.getAccessToken() != null
}
