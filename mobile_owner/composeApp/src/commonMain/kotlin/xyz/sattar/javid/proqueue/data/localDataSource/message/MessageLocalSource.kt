package xyz.sattar.javid.proqueue.data.localDataSource.message

import xyz.sattar.javid.proqueue.domain.model.message.Message

/**
 * Plain-interface indirection over [MessageDao] so the repository layer does
 * not depend on Room directly (Room has no web target). See
 * [xyz.sattar.javid.proqueue.data.localDataSource.appointment.AppointmentLocalSource]
 * for the full rationale. Speaks in [Message] (domain model), not the
 * Room-annotated `MessageEntity`.
 */
interface MessageLocalSource {
    suspend fun insertMessage(message: Message)

    suspend fun getAppointmentMessages(appointmentId: Long): List<Message>

    suspend fun getMessagesForVisitorAndBusiness(visitorId: Long, businessId: Long): List<Message>

    suspend fun deleteMessage(id: Long): Int

    suspend fun deleteMessagesByVisitorId(visitorId: Long): Int
}
