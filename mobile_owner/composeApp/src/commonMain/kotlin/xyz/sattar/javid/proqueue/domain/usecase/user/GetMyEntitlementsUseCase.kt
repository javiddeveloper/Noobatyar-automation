package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.domain.UserRepository

class GetMyEntitlementsUseCase(private val repository: UserRepository) {
    suspend operator fun invoke() = repository.getMyEntitlements()
}
