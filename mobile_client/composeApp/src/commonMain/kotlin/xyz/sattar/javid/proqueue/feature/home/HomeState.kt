package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto
import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.message.Message

@Immutable
data class HomeState(
    val isLoading: Boolean = false,
    val business: Business? = null,
    val message: String? = null,
    val queue: List<QueueItem> = emptyList(),
    val stats: DashboardStats = DashboardStats(),
    val plans: List<PlanDto> = emptyList(),
    val paymentResult: PaymentResultInfo? = null
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class ShowMessage(val message: String) : PartialState()
        data class LoadBusinessName(val business: Business?) : PartialState()
        data class LoadQueue(val queue: List<QueueItem>) : PartialState()
        data class LoadStats(val stats: DashboardStats) : PartialState()
        data class LoadPlans(val plans: List<PlanDto>) : PartialState()
        data class ShowPaymentResult(val info: PaymentResultInfo?) : PartialState()
    }
}

data class PaymentResultInfo(
    val success: Boolean,
    val ref: String? = null,
    val amount: String? = null,
    val txn: String? = null
)

data class QueueItem(
    val appointment: Appointment,
    val visitorName: String,
    val visitorPhone: String,
    val estimatedStartTime: Long,
    val estimatedEndTime: Long,
    val messages: List<Message> = emptyList()
){
    val overdue = DateTimeUtils.systemCurrentMilliseconds() > estimatedEndTime && appointment.status == "WAITING"
    val waitingText =
        if (overdue) "زمان رد شده"
        else DateTimeUtils.calculateWaitingTime(estimatedStartTime)
}

data class DashboardStats(
    val totalAppointments: Int = 0,
    val completedAppointments: Int = 0,
    val noShowAppointments: Int = 0,
    val totalVisitors: Int = 0
)
