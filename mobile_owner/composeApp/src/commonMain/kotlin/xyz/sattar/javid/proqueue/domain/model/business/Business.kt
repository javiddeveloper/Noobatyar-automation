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
    val notificationMinutesBefore: Int = DEFAULT_REMINDER_MINUTES,
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
    val logoBytes: ByteArray? = null,
    /** Emergency notice for the public booking page — hidden while disabled. */
    val noticeEnabled: Boolean = false,
    val noticeMessage: String = "",
    /**
     * Whether the client gets an appointment reminder at all. Off means no
     * reminder is sent on any channel; [reminderDelivery] only decides *how* an
     * enabled reminder travels.
     */
    val enableReminderSms: Boolean = true,
    /** [ReminderDelivery] value; PANEL needs the auto_reminder_sms entitlement. */
    val reminderDelivery: String = ReminderDelivery.MANUAL.value,
    /**
     * The services this business offers, defined once in the business screen.
     * Drives the chip picker both here (recording what a visitor received) and
     * on the client's public booking page (saying what they're coming for).
     */
    val services: List<String> = emptyList(),
    /**
     * Whether a client may type a service that isn't on [services]. Off by
     * default — an owner who leaves it off gets answers they can plan slot
     * lengths around instead of free text.
     */
    val allowClientAddService: Boolean = false
) {
    /** True only when both the content review and the plan/billing state allow it. */
    val isPubliclyVisible: Boolean
        get() = moderationStatus?.isPubliclyVisible == true && !isLocked
}
