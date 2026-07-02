package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.domain.UserRepository
import xyz.sattar.javid.proqueue.domain.model.VersionInfo

class CheckVersionUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(versionName: String): ApiResponse<VersionInfo> {
        return repository.checkVersion(versionName)
    }
}
