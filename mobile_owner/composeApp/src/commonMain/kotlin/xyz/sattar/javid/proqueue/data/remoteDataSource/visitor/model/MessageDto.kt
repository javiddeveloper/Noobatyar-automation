package xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    @SerialName("id") val id: Long,
    @SerialName("visitor") val visitorId: Long,
    @SerialName("business") val businessId: Long,
    @SerialName("message_text") val messageText: String,
    @SerialName("status") val status: String,
    @SerialName("sent_at") val sentAt: String
)
