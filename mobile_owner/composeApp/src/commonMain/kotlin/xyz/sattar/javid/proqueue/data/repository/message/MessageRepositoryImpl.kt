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

    override suspend fun syncMessages(visitorId: Long, businessId: Long): Boolean {
        return try {
            val response = visitorApiService.getVisitorMessages(visitorId = visitorId)
            if (response is ApiResponse.Success) {
                val dtos = response.data.results
                val entities = dtos.map { dto ->
                    xyz.sattar.javid.proqueue.data.localDataSource.message.MessageEntity(
                        id = dto.id,
                        appointmentId = dto.appointmentId ?: 0L,
                        messageType = dto.messageType,
                        content = dto.content,
                        sentAt = DateTimeUtils.parseIsoToEpochMillis(dto.sentAt),
                        businessTitle = dto.businessTitle ?: "--"
                    )
                }
                // Option: Delete all existing messages for visitor and business and re-insert, or upsert.
                // It's safer to just iterate and insert with Ignore/Replace since they have ids
                entities.forEach { messageDao.insertMessage(it) }
                true
            } else if (response is ApiResponse.Error) {
                throw Exception(response.message)
            } else {
                false
            }
        } catch (e: Exception) {
            throw e
        }
    }
}