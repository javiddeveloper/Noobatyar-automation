package xyz.sattar.javid.proqueue.core.notifications

interface NotificationScheduler {
    fun scheduleReminder(
        appointmentId: Long,
        customerName: String,
        businessName: String,
        triggerAtMillis: Long,
        minutesBefore: Int,
        businessId: Long,
        visitorId: Long
    )
    fun cancelReminder(appointmentId: Long)
    suspend fun hasPermission(): Boolean
}
