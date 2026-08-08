package xyz.sattar.javid.proqueue.data.localDataSource.business

import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.CreateBusinessRequestDto
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.business.BusinessCategory
import xyz.sattar.javid.proqueue.domain.model.business.ModerationStatus

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
    allowAnonymousView = allowAnonymousView,
    notifyOwnerBySms = notifyOwnerBySms,
    paymentMethod = paymentMethod,
    acceptedPaymentMethods = acceptedPaymentMethods.split(",").filter { it.isNotEmpty() },
    maxAppointmentsPerHour = maxAppointmentsPerHour,
    depositAmount = depositAmount,
    merchantId = merchantId,
    paymentLink = paymentLink,
    cardNumber = cardNumber,
    cardOwnerName = cardOwnerName,
    bio = bio,
    moderationStatus = ModerationStatus.fromString(moderationStatus),
    moderationStatusDisplay = moderationStatusDisplay,
    moderationNote = moderationNote,
    moderationSubmittedAt = moderationSubmittedAt
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
    allowAnonymousView = allowAnonymousView,
    notifyOwnerBySms = notifyOwnerBySms,
    paymentMethod = paymentMethod,
    acceptedPaymentMethods = acceptedPaymentMethods?.joinToString(",") ?: "",
    maxAppointmentsPerHour = maxAppointmentsPerHour,
    depositAmount = depositAmount,
    merchantId = merchantId,
    paymentLink = paymentLink,
    cardNumber = cardNumber,
    cardOwnerName = cardOwnerName,
    bio = bio,
    moderationStatus = moderationStatus?.value,
    moderationStatusDisplay = moderationStatusDisplay,
    moderationNote = moderationNote,
    moderationSubmittedAt = moderationSubmittedAt
)

fun Business.toRequestDto() = CreateBusinessRequestDto(
    title = title,
    category = category.value,
    phone = phone,
    address = address,
    defaultServiceDuration = defaultServiceDuration,
    workStartHour = workStartHour,
    workEndHour = workEndHour,
    notificationEnabled = notificationEnabled,
    notificationTypes = notificationTypes,
    notificationMinutesBefore = notificationMinutesBefore,
    allowAnonymousView = allowAnonymousView,
    notifyOwnerBySms = notifyOwnerBySms,
    paymentMethod = paymentMethod,
    acceptedPaymentMethods = acceptedPaymentMethods,
    maxAppointmentsPerHour = maxAppointmentsPerHour,
    depositAmount = depositAmount,
    merchantId = merchantId,
    paymentLink = paymentLink,
    cardNumber = cardNumber,
    cardOwnerName = cardOwnerName,
    bio = bio
)

fun BusinessDto.toEntity(): BusinessEntity {
    val epochMillis = xyz.sattar.javid.proqueue.core.utils.DateTimeUtils.parseIsoToEpochMillis(this.createdAt)

    return BusinessEntity(
        id = id,
        title = title,
        category = category,
        uniqueCode = uniqueCode,
        phone = phone,
        address = address,
        logoPath = logo ?: "",
        defaultServiceDuration = defaultServiceDuration,
        workStartHour = workStartHour,
        workEndHour = workEndHour,
        notificationEnabled = notificationEnabled,
        notificationTypes = notificationTypes,
        notificationMinutesBefore = notificationMinutesBefore,
        createdAt = epochMillis,
        allowAnonymousView = allowAnonymousView,
        notifyOwnerBySms = notifyOwnerBySms,
        paymentMethod = paymentMethod,
        acceptedPaymentMethods = acceptedPaymentMethods?.joinToString(",") ?: "",
        maxAppointmentsPerHour = maxAppointmentsPerHour,
        depositAmount = depositAmount,
        merchantId = merchantId ?: "",
        paymentLink = paymentLink ?: "",
        cardNumber = cardNumber ?: "",
        cardOwnerName = cardOwnerName ?: "",
        bio = bio ?: "",
        moderationStatus = moderationStatus,
        moderationStatusDisplay = moderationStatusDisplay ?: "",
        moderationNote = moderationNote ?: "",
        // 0 when absent or unparsable — treated as "unknown" by the UI.
        moderationSubmittedAt = moderationSubmittedAt
            ?.let { xyz.sattar.javid.proqueue.core.utils.DateTimeUtils.parseIsoToEpochMillis(it) }
            ?: 0L
    )
}
