package xyz.sattar.javid.proqueue.domain

import xyz.sattar.javid.proqueue.domain.model.message.Message

interface MessageRepository {
    suspend fun insertMessage(message: Message): Boolean
    suspend fun getAppointmentMessages(appointmentId: Long): List<Message>
    suspend fun getMessagesForVisitorAndBusiness(visitorId: Long, businessId: Long): List<Message>
    suspend fun deleteMessage(id: Long): Boolean
    suspend fun deleteMessagesByVisitorId(visitorId: Long): Boolean
}
