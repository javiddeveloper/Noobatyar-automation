package xyz.sattar.javid.proqueue.domain.model.message

data class Message(
    val id: Long,
    val appointmentId: Long,
    val messageType: String,
    val content: String,
    val sentAt: Long,
    val businessTitle: String,
) {
}
