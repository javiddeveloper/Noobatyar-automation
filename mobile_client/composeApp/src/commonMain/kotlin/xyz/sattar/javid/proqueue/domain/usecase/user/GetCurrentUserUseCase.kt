package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.domain.UserRepository

class GetCurrentUserUseCase(private val repository: UserRepository) {
    operator fun invoke() = repository.getCurrentUser()
}
