package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.domain.UserRepository

class GetMySubscriptionUseCase(private val repository: UserRepository) {
    suspend operator fun invoke() = repository.getMySubscription()
}
