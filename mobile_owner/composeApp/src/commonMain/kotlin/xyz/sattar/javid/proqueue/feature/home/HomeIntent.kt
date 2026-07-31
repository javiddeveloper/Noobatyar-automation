package xyz.sattar.javid.proqueue.feature.home

sealed interface HomeIntent {
    data object LoadData : HomeIntent
    /** فقط صف نوبت را به‌روز کن (بدون دریافت دوباره پلن/اشتراک/entitlements) */
    data object RefreshQueue : HomeIntent
    data class RemoveAppointment(val appointmentId: Long) : HomeIntent
    data class MarkAppointmentCompleted(val appointmentId: Long) : HomeIntent
    data class MarkAppointmentNoShow(val appointmentId: Long) : HomeIntent
    data class SendMessage(
        val appointmentId: Long,
        val type: String,
        val content: String,
        val businessTitle: String
    ) : HomeIntent
    data class PurchasePlan(val planId: Int) : HomeIntent
}
