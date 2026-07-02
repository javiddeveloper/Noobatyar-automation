package xyz.sattar.javid.proqueue.data.localDataSource.business

import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto

import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.business.BusinessCategory

fun BusinessEntity.toDomain() = Business(
    id = id,
    title = title,
    category = BusinessCategory.fromString(category),
    uniqueCode = uniqueCode,
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
    allowAnonymousView = allowAnonymousView
)

fun Business.toEntity() = BusinessEntity(
    id = id,
    title = title,
    category = category.value,
    uniqueCode = uniqueCode,
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
    allowAnonymousView = allowAnonymousView
)



fun BusinessDto.toEntity(): BusinessEntity {
    val epochMillis = this.createdAt?.let { xyz.sattar.javid.proqueue.core.utils.DateTimeUtils.parseIsoToEpochMillis(it) } ?: 0L

    return BusinessEntity(
        id = id,
        title = title,
        category = category,
        uniqueCode = uniqueCode,
        phone = phone,
        address = address,
        logoPath = logo,
        defaultServiceDuration = defaultServiceDuration,
        workStartHour = workStartHour,
        workEndHour = workEndHour,
        notificationEnabled = notificationEnabled,
        notificationTypes = notificationTypes,
        notificationMinutesBefore = notificationMinutesBefore,
        createdAt = epochMillis,
        allowAnonymousView = allowAnonymousView
    )
}
