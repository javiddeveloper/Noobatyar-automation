package xyz.sattar.javid.proqueue.data.repository.message

import xyz.sattar.javid.proqueue.data.localDataSource.message.MessageDao
import xyz.sattar.javid.proqueue.data.localDataSource.message.toDomain
import xyz.sattar.javid.proqueue.data.localDataSource.message.toEntity
import xyz.sattar.javid.proqueue.domain.MessageRepository
import xyz.sattar.javid.proqueue.domain.model.message.Message

import xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.VisitorApiService
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils

class MessageRepositoryImpl(
    private val messageDao: MessageDao,
    private val visitorApiService: VisitorApiService
) : MessageRepository {
    override suspend fun insertMessage(message: Message): Boolean {
        return try {
            messageDao.insertMessage(message.toEntity())
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun getAppointmentMessages(appointmentId: Long): List<Message> {
        return try {
            messageDao.getAppointmentMessages(appointmentId).map { it.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getMessagesForVisitorAndBusiness(visitorId: Long, businessId: Long): List<Message> {
        return try {
            messageDao.getMessagesForVisitorAndBusiness(visitorId, businessId).map { it.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun deleteMessage(id: Long): Boolean {
        return try {
            messageDao.deleteMessage(id) > 0
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun deleteMessagesByVisitorId(visitorId: Long): Boolean {
        return try {
            messageDao.deleteMessagesByVisitorId(visitorId) >= 0
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun getRemoteMessages(visitorId: Long, businessId: Long, businessTitle: String): List<Message> {
        val response = visitorApiService.getVisitorMessages(visitorId = visitorId, businessId = businessId)
        return when (response) {
            is ApiResponse.Success -> response.data.results.map { dto ->
                Message(
                    id = dto.id,
                    appointmentId = 0L,
                    messageType = "SMS",
                    content = dto.messageText,
                    sentAt = DateTimeUtils.parseIsoToEpochMillis(dto.sentAt),
                    businessTitle = businessTitle,
                    remote = true,
                    status = dto.status
                )
            }
            is ApiResponse.Error -> throw Exception(response.message)
            else -> emptyList()
        }
    }
}