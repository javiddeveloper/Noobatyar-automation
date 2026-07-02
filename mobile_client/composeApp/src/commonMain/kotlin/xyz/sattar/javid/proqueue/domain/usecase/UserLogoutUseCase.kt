package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.UserRepository

class UserLogoutUseCase(private val repository: UserRepository) {
    suspend operator fun invoke() =
        repository.logout()
}