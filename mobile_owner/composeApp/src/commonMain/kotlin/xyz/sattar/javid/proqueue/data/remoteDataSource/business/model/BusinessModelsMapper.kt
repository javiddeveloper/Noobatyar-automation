package xyz.sattar.javid.proqueue.data.remoteDataSource.business.model

import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.business.BusinessCategory
import xyz.sattar.javid.proqueue.domain.model.business.ModerationStatus
import xyz.sattar.javid.proqueue.domain.model.business.ReminderDelivery

/**
 * DTO -> domain, straight across (no Room `BusinessEntity` in between — see
 * docs/OWNER_WEB_PLAN.md section 5). Derived field-for-field from the
 * previously existing `BusinessDto.toEntity()` + `BusinessEntity.toDomain()`
 * pair (now in roomMain/.../business/BusinessMapper.kt) so the net result on
 * every field is identical to what that round trip produced:
 * - `acceptedPaymentMethods`: the old path joined to a comma-string then
 *   split+filtered blanks back out, always landing on a non-null (possibly
 *   empty) list — reproduced directly with `?: emptyList()` + filter.
 * - `services`: same join/split roundtrip, reproduced with trim + filter.
 * - `depositMode`: neither the old entity mapper nor the old domain mapper
 *   ever carried it through (BusinessEntity.depositMode was written by
 *   nothing but its own default) — left at the Business default here too,
 *   not a regression introduced by this refactor.
 */
fun BusinessDto.toDomain(): Business {
    val epochMillis = DateTimeUtils.parseIsoToEpochMillis(this.createdAt)

    return Business(
        id = id,
        title = title,
        category = BusinessCategory.fromString(category),
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
        acceptedPaymentMethods = (acceptedPaymentMethods ?: emptyList()).filter { it.isNotEmpty() },
        maxAppointmentsPerHour = maxAppointmentsPerHour,
        depositAmount = depositAmount,
        merchantId = merchantId ?: "",
        paymentLink = paymentLink ?: "",
        cardNumber = cardNumber ?: "",
        cardOwnerName = cardOwnerName ?: "",
        bio = bio ?: "",
        moderationStatus = ModerationStatus.fromString(moderationStatus),
        moderationStatusDisplay = moderationStatusDisplay ?: "",
        moderationNote = moderationNote ?: "",
        // 0 when absent or unparsable — treated as "unknown" by the UI.
        moderationSubmittedAt = moderationSubmittedAt
            ?.let { DateTimeUtils.parseIsoToEpochMillis(it) }
            ?: 0L,
        isLocked = isLocked,
        noticeEnabled = noticeEnabled,
        noticeMessage = noticeMessage ?: "",
        enableReminderSms = enableReminderSms,
        reminderDelivery = reminderDelivery ?: ReminderDelivery.MANUAL.value,
        services = services.map { it.trim() }.filter { it.isNotEmpty() },
        allowClientAddService = allowClientAddService
    )
}
