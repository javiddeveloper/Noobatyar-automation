package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.MessageRepository
import xyz.sattar.javid.proqueue.domain.model.message.Message
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class SendMessageUseCase(private val repository: MessageRepository) {
    @OptIn(ExperimentalTime::class)
    suspend operator fun invoke(
        appointmentId: Long,
        type: String,
        content: String,
        businessTitle: String
    ): Boolean {
        val message = Message(
            id = 0,
            appointmentId = appointmentId,
            messageType = type,
            content = content,
            sentAt = Clock.System.now().toEpochMilliseconds(),
            businessTitle = businessTitle
        )
        return repository.insertMessage(message)
    }
}
