package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SubscriptionDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementsResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.DailyCountDto
import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.message.Message

@Immutable
data class HomeState(
    val isLoading: Boolean = false,
    val business: Business? = null,
    val message: UiMessage? = null,
    val queue: List<QueueItem> = emptyList(),
    val stats: DashboardStats = DashboardStats(),
    val statsLoaded: Boolean = false,
    val plans: List<PlanDto> = emptyList(),
    val plansLoaded: Boolean = false,
    val subscription: SubscriptionDto? = null,
    val entitlements: EntitlementsResponseDto? = null,
    val entitlementsLoaded: Boolean = false,
    val dailyCounts: List<DailyCountDto> = emptyList(),
    val chartLoaded: Boolean = false,
    val monthOverview: MonthOverview? = null
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        /** Clears section-ready flags so shimmer placeholders show again. */
        data object ResetSectionLoaders : PartialState()
        data class ShowMessage(val message: UiMessage) : PartialState()
        data object ClearMessage : PartialState()
        data class LoadBusinessName(val business: Business?) : PartialState()
        data class LoadQueue(val queue: List<QueueItem>) : PartialState()
        data class LoadStats(val stats: DashboardStats) : PartialState()
        data class LoadPlans(val plans: List<PlanDto>) : PartialState()
        data class LoadSubscription(val subscription: SubscriptionDto?) : PartialState()
        data class LoadEntitlements(val entitlements: EntitlementsResponseDto?) : PartialState()
        data class LoadDailyCounts(val counts: List<DailyCountDto>) : PartialState()
        data class LoadMonthOverview(val overview: MonthOverview?) : PartialState()
    }
}

/**
 * One month's worth of appointment counts, for the home screen's month card.
 *
 * [dailyCounts] is kept alongside [total] so the card can draw the shape of the
 * month (which days are busy) rather than only its sum — a month with 20
 * appointments spread evenly and one with all 20 on a single day are the same
 * number and very different weeks.
 *
 * [rangeStart]/[rangeEndExclusive] are carried so tapping the card can open the
 * visitors list over exactly the range shown, instead of the list recomputing
 * month boundaries and risking a different answer.
 */
@Immutable
data class MonthBucket(
    val monthIndex: Int,
    val label: String,
    val total: Int,
    val dailyCounts: List<Int> = emptyList(),
    val rangeStart: Long = 0L,
    val rangeEndExclusive: Long = 0L,
)

@Immutable
data class MonthOverview(
    val previous: MonthBucket,
    val current: MonthBucket,
    val next: MonthBucket,
) {
    val buckets: List<MonthBucket> get() = listOf(previous, current, next)
}

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
    val cancelledAppointments: Int = 0,
    val totalVisitors: Int = 0
)
