package xyz.sattar.javid.proqueue.domain

import xyz.sattar.javid.proqueue.domain.model.message.Message

interface MessageRepository {
    suspend fun insertMessage(message: Message): Boolean
    suspend fun getAppointmentMessages(appointmentId: Long): List<Message>
    suspend fun getMessagesForVisitorAndBusiness(visitorId: Long, businessId: Long): List<Message>
    suspend fun deleteMessage(id: Long): Boolean
    suspend fun deleteMessagesByVisitorId(visitorId: Long): Boolean
    /**
     * Server-side SMS delivery history for a visitor. Not cached locally: these
     * records aren't tied to an appointment, so they don't fit the local
     * appointment-scoped Message table.
     */
    suspend fun getRemoteMessages(visitorId: Long, businessId: Long, businessTitle: String): List<Message>
}
