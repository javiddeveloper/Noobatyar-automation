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
    val logoBytes: ByteArray? = null,
    /** Emergency notice for the public booking page — hidden while disabled. */
    val noticeEnabled: Boolean = false,
    val noticeMessage: String = "",
    /** [ReminderDelivery] value; PANEL needs the auto_reminder_sms entitlement. */
    val reminderDelivery: String = ReminderDelivery.MANUAL.value
)