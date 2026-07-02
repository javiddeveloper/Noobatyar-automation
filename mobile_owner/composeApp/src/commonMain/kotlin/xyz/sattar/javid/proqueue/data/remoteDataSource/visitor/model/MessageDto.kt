package xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    @SerialName("id") val id: Long,
    @SerialName("appointment") val appointmentId: Long?,
    @SerialName("business_title") val businessTitle: String?,
    @SerialName("message_type") val messageType: String,
    @SerialName("content") val content: String,
    @SerialName("sent_at") val sentAt: String
)
