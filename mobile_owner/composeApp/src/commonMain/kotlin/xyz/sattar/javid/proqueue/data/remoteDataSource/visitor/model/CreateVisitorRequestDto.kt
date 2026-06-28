package xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateVisitorRequestDto(
    @SerialName("full_name") val fullName: String,
    @SerialName("phone_number") val phoneNumber: String
)
