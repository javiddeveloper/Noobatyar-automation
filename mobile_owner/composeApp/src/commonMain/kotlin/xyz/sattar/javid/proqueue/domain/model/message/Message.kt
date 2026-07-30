package xyz.sattar.javid.proqueue.domain.model.message

data class Message(
    val id: Long,
    val appointmentId: Long,
    val messageType: String,
    val content: String,
    val sentAt: Long,
    val businessTitle: String,
    /**
     * True for SMS delivery records that live on the server (SmsLog). They are
     * read-only history — not backed by a local row, so they can't be deleted.
     */
    val remote: Boolean = false,
    /** Server delivery status (SENT/FAILED); null for locally composed messages. */
    val status: String? = null,
) {
}
