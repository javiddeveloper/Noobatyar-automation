package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdateUserProfileRequestDto(
    val name: String? = null
)
