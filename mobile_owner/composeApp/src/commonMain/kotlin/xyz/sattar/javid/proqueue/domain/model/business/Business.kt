package xyz.sattar.javid.proqueue.domain.model.business

data class Business(
    val id: Long = 0,
    val title: String,
    val category: BusinessCategory,
    val uniqueCode: String? = null,
    val phone: String,
    val address: String,
    val logoPath: String,
    val defaultServiceDuration: Int,
    val workStartHour: Int,
    val workEndHour: Int,
    val notificationEnabled: Boolean,
    val notificationTypes: String,
    val notificationMinutesBefore: Int = 0,
    val createdAt: Long = 0,
    val allowAnonymousView: Boolean = false,
    val notifyOwnerBySms: Boolean = true,
    val paymentMethod: String? = null,
    val acceptedPaymentMethods: List<String>? = null,
    val maxAppointmentsPerHour: Int? = null,
    val depositMode: String? = null,
    val depositAmount: Int? = null,
    val merchantId: String = "",
    val paymentLink: String = "",
    val cardNumber: String = "",
    val cardOwnerName: String = "",
    val bio: String = "",
    // Moderation. Null status = the backend didn't report one (older server),
    // in which case the UI stays silent about moderation altogether.
    val moderationStatus: ModerationStatus? = null,
    val moderationStatusDisplay: String = "",
    val moderationNote: String = "",
    val moderationSubmittedAt: Long = 0,
    // Billing lock (subscription/plan limits) — independent of moderationStatus.
    // See ModerationStatus's kdoc: the two answer different questions and a
    // visibility check that only looks at one of them is wrong.
    val isLocked: Boolean = false,
    val logoBytes: ByteArray? = null
) {
    /** True only when both the content review and the plan/billing state allow it. */
    val isPubliclyVisible: Boolean
        get() = moderationStatus?.isPubliclyVisible == true && !isLocked
}