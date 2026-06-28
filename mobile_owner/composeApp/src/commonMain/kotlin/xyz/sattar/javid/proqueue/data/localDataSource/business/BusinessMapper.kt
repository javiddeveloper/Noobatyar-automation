package xyz.sattar.javid.proqueue.data.localDataSource.business

import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.CreateBusinessRequestDto
import xyz.sattar.javid.proqueue.domain.model.business.Business

fun BusinessEntity.toDomain() = Business(
    id = id,
    title = title,
    phone = phone,
    address = address,
    logoPath = logoPath,
    defaultServiceDuration = defaultServiceDuration,
    workStartHour = workStartHour,
    workEndHour = workEndHour,
    notificationEnabled = notificationEnabled,
    notificationTypes = notificationTypes,
    notificationMinutesBefore = notificationMinutesBefore,
    createdAt = createdAt,
)

fun Business.toEntity() = BusinessEntity(
    id = id,
    title = title,
    phone = phone,
    address = address,
    logoPath = logoPath,
    defaultServiceDuration = defaultServiceDuration,
    workStartHour = workStartHour,
    workEndHour = workEndHour,
    notificationEnabled = notificationEnabled,
    notificationTypes = notificationTypes,
    notificationMinutesBefore = notificationMinutesBefore,
    createdAt = createdAt,
)

fun Business.toRequestDto() = CreateBusinessRequestDto(
    title = title,
    phone = phone,
    address = address,
    defaultServiceDuration = defaultServiceDuration,
    workStartHour = workStartHour,
    workEndHour = workEndHour,
    notificationEnabled = notificationEnabled,
    notificationTypes = notificationTypes,
    notificationMinutesBefore = notificationMinutesBefore
)

fun BusinessDto.toEntity(): BusinessEntity {
    val epochMillis = xyz.sattar.javid.proqueue.core.utils.DateTimeUtils.parseIsoToEpochMillis(this.createdAt)

    return BusinessEntity(
        id = id,
        title = title,
        phone = phone,
        address = address,
        logoPath = logo ?: "",
        defaultServiceDuration = defaultServiceDuration,
        workStartHour = workStartHour,
        workEndHour = workEndHour,
        notificationEnabled = notificationEnabled,
        notificationTypes = notificationTypes,
        notificationMinutesBefore = notificationMinutesBefore,
        createdAt = epochMillis
    )
}
