package xyz.sattar.javid.proqueue.data.localDataSource.message

import xyz.sattar.javid.proqueue.domain.model.message.Message

fun MessageEntity.toDomain() = Message(
    id = id,
    appointmentId = appointmentId,
    messageType = messageType,
    content = content,
    sentAt = sentAt,
    businessTitle = businessTitle
)

fun Message.toEntity() = MessageEntity(
    id = id,
    appointmentId = appointmentId,
    messageType = messageType,
    content = content,
    sentAt = sentAt,
    businessTitle = businessTitle
)
