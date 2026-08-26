package xyz.sattar.javid.proqueue.data.localDataSource.message

import xyz.sattar.javid.proqueue.domain.model.message.Message

/**
 * Adapter that sits between [MessageDao] (Room, speaks `MessageEntity`) and
 * [MessageLocalSource] (the interface the repository actually depends on,
 * speaks [Message]). See docs/OWNER_WEB_PLAN.md section 5.
 */
class RoomMessageLocalSource(
    private val dao: MessageDao
) : MessageLocalSource {
    override suspend fun insertMessage(message: Message) =
        dao.insertMessage(message.toEntity())

    override suspend fun getAppointmentMessages(appointmentId: Long): List<Message> =
        dao.getAppointmentMessages(appointmentId).map { it.toDomain() }

    override suspend fun getMessagesForVisitorAndBusiness(visitorId: Long, businessId: Long): List<Message> =
        dao.getMessagesForVisitorAndBusiness(visitorId, businessId).map { it.toDomain() }

    override suspend fun deleteMessage(id: Long): Int =
        dao.deleteMessage(id)

    override suspend fun deleteMessagesByVisitorId(visitorId: Long): Int =
        dao.deleteMessagesByVisitorId(visitorId)
}
