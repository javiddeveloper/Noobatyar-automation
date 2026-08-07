package xyz.sattar.javid.proqueue.feature.home

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.usecase.GetTodayStatsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetWaitingQueueUseCase
import xyz.sattar.javid.proqueue.domain.usecase.MarkAppointmentCompletedUseCase
import xyz.sattar.javid.proqueue.domain.usecase.MarkAppointmentNoShowUseCase
import xyz.sattar.javid.proqueue.domain.usecase.RemoveAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SendMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SyncAppointmentsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetDailyCountsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetPlansUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.CreatePaymentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetMySubscriptionUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetMyEntitlementsUseCase
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.sortedForBanner
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.jetbrains.compose.resources.getString
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.home_sms_quota_exhausted_warning


class HomeViewModel(
    private val getWaitingQueueUseCase: GetWaitingQueueUseCase,
    private val getTodayStatsUseCase: GetTodayStatsUseCase,
    private val removeAppointmentUseCase: RemoveAppointmentUseCase,
    private val markAppointmentCompletedUseCase: MarkAppointmentCompletedUseCase,
    private val markAppointmentNoShowUseCase: MarkAppointmentNoShowUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val getPlansUseCase: GetPlansUseCase,
    private val createPaymentUseCase: CreatePaymentUseCase,
    private val syncAppointmentsUseCase: SyncAppointmentsUseCase,
    private val getMySubscriptionUseCase: GetMySubscriptionUseCase,
    private val getMyEntitlementsUseCase: GetMyEntitlementsUseCase,
    private val getDailyCountsUseCase: GetDailyCountsUseCase
) : BaseViewModel<HomeState, HomeState.PartialState, HomeEvent, HomeIntent>(
    initialState = HomeState()
) {
    /** Business id the SMS-quota-exhausted warning was last shown for this session. */
    private var smsQuotaWarningShownFor: Long? = null

    init {
        viewModelScope.launch {
            BusinessStateHolder.selectedBusiness.collectLatest {
                sendIntent(HomeIntent.LoadData)
            }
        }
    }

    override fun handleIntent(intent: HomeIntent): Flow<HomeState.PartialState> {
        return when (intent) {
            HomeIntent.LoadData -> loadData()
            HomeIntent.RefreshQueue -> refreshQueueAndStats()
            is HomeIntent.RemoveAppointment -> removeAppointment(intent.appointmentId)
            is HomeIntent.MarkAppointmentCompleted -> markCompleted(intent.appointmentId)
            is HomeIntent.MarkAppointmentNoShow -> markNoShow(intent.appointmentId)
            is HomeIntent.SendMessage -> sendMessage(intent.appointmentId, intent.type, intent.content, intent.businessTitle)
            is HomeIntent.PurchasePlan -> purchasePlan(intent.planId)
            HomeIntent.ClearMessage -> flow { emit(HomeState.PartialState.ClearMessage) }
        }
    }

    override fun reduceState(
        currentState: HomeState,
        partialState: HomeState.PartialState
    ): HomeState {
        return when (partialState) {
            is HomeState.PartialState.IsLoading ->
                currentState.copy(isLoading = partialState.isLoading)
            HomeState.PartialState.ResetSectionLoaders ->
                currentState.copy(
                    statsLoaded = false,
                    chartLoaded = false,
                    entitlementsLoaded = false,
                    plansLoaded = false
                )
            is HomeState.PartialState.ShowMessage ->
                currentState.copy(message = partialState.message, isLoading = false)
            is HomeState.PartialState.LoadBusinessName ->
                currentState.copy(business = partialState.business)
            is HomeState.PartialState.LoadQueue ->
                currentState.copy(queue = partialState.queue)
            is HomeState.PartialState.LoadStats ->
                currentState.copy(stats = partialState.stats, statsLoaded = true)
            is HomeState.PartialState.LoadPlans ->
                currentState.copy(plans = partialState.plans, plansLoaded = true)
            is HomeState.PartialState.LoadSubscription ->
                currentState.copy(subscription = partialState.subscription)
            is HomeState.PartialState.LoadEntitlements ->
                currentState.copy(
                    entitlements = partialState.entitlements,
                    entitlementsLoaded = true
                )
            is HomeState.PartialState.LoadDailyCounts ->
                currentState.copy(dailyCounts = partialState.counts, chartLoaded = true)
            HomeState.PartialState.ClearMessage ->
                currentState.copy(message = null)
        }
    }

    override fun createErrorState(message: String): HomeState.PartialState =
        HomeState.PartialState.ShowMessage(UiMessage.error(message))

    /**
     * بار گذاری کامل: پلن‌ها + اشتراک + entitlements + queue + stats + chart
     * فقط هنگام تغییر business یا اولین بار ورود فراخوانی می‌شود
     */
    private fun loadData(): Flow<HomeState.PartialState> = flow {
        emit(HomeState.PartialState.ResetSectionLoaders)
        emit(HomeState.PartialState.IsLoading(true))
        val business = BusinessStateHolder.selectedBusiness.value
        emit(HomeState.PartialState.LoadBusinessName(business))

        // پلن‌ها — فقط اگر قبلاً نگرفتیم یا لیست خالی بود
        // ترتیب نمایش: ابتدا پلن آزمایشی، سپس بقیه به ترتیب صعودی مدت.
        try {
            when (val plansResponse = getPlansUseCase()) {
                is ApiResponse.Success -> emit(HomeState.PartialState.LoadPlans(plansResponse.data.sortedForBanner()))
                is ApiResponse.Error -> emit(HomeState.PartialState.LoadPlans(emptyList()))
            }
        } catch (e: Exception) {
            emit(HomeState.PartialState.LoadPlans(emptyList()))
        }

        // اشتراک کاربر
        try {
            when (val subResponse = getMySubscriptionUseCase()) {
                is ApiResponse.Success -> emit(HomeState.PartialState.LoadSubscription(subResponse.data))
                is ApiResponse.Error -> {}
            }
        } catch (e: Exception) {}

        // Entitlements
        try {
            when (val entResponse = getMyEntitlementsUseCase()) {
                is ApiResponse.Success -> {
                    emit(HomeState.PartialState.LoadEntitlements(entResponse.data))
                    val skipped = entResponse.data.usage.sms.skippedThisMonth
                    // Real count of messages the server actually skipped this month
                    // (SmsLog rows written with status SKIPPED_QUOTA) — not a guess
                    // from monthlyRemaining hitting zero. Shown once per business
                    // per app session so it doesn't repeat on every pull-to-refresh.
                    val business = BusinessStateHolder.selectedBusiness.value
                    if (skipped > 0 && business != null && smsQuotaWarningShownFor != business.id) {
                        smsQuotaWarningShownFor = business.id
                        emit(
                            HomeState.PartialState.ShowMessage(
                                UiMessage.warning(
                                    getString(
                                        Res.string.home_sms_quota_exhausted_warning,
                                        skipped
                                    )
                                )
                            )
                        )
                    }
                }
                is ApiResponse.Error -> emit(HomeState.PartialState.LoadEntitlements(null))
            }
        } catch (e: Exception) {
            emit(HomeState.PartialState.LoadEntitlements(null))
        }

        // Queue + Stats + Chart
        emitAll(refreshQueueAndStats())
    }

    /**
     * به‌روز‌رسانی سبک: فقط صف نوبت + آمار + نمودار
     * بعد از هر تغییری در نوبت‌ها (حذف/تکمیل/غیب) یا دکمه Refresh فراخوانی می‌شود
     */
    private fun refreshQueueAndStats(): Flow<HomeState.PartialState> = flow {
        emit(HomeState.PartialState.IsLoading(true))
        val business = BusinessStateHolder.selectedBusiness.value
        if (business != null) {
            try {
                @OptIn(ExperimentalTime::class)
                val today = Clock.System.now().toEpochMilliseconds()
                syncAppointmentsUseCase(business.id, date = today)

                val queue = getWaitingQueueUseCase(business.id, today)
                // BaseViewModel's intent pipeline uses flatMapMerge (concurrent,
                // not cancelling superseded work), so if the user switches to a
                // different business while this business's network calls are
                // still in flight, its results can otherwise land *after* the
                // newer business's and silently overwrite the stats/queue with
                // stale numbers from the business that's no longer selected.
                // Re-check right before each emit and drop stale results.
                if (BusinessStateHolder.selectedBusiness.value?.id != business.id) return@flow
                emit(HomeState.PartialState.LoadQueue(calculateQueueTimes(queue)))

                val stats = getTodayStatsUseCase(business.id)
                if (BusinessStateHolder.selectedBusiness.value?.id != business.id) return@flow
                emit(HomeState.PartialState.LoadStats(stats))

                val daily = getDailyCountsUseCase(business.id, 7)
                if (BusinessStateHolder.selectedBusiness.value?.id != business.id) return@flow
                emit(HomeState.PartialState.LoadDailyCounts(daily))
            } catch (e: Exception) {
                if (BusinessStateHolder.selectedBusiness.value?.id != business.id) return@flow
                emit(HomeState.PartialState.LoadStats(DashboardStats()))
                emit(HomeState.PartialState.LoadDailyCounts(emptyList()))
                emit(HomeState.PartialState.ShowMessage(UiMessage.error(e.message ?: "خطا در بارگذاری")))
            }
        } else {
            emit(HomeState.PartialState.LoadStats(DashboardStats()))
            emit(HomeState.PartialState.LoadDailyCounts(emptyList()))
        }
        emit(HomeState.PartialState.IsLoading(false))
    }

    private fun purchasePlan(planId: Int): Flow<HomeState.PartialState> = flow {
        emit(HomeState.PartialState.IsLoading(true))
        try {
            when (val response = createPaymentUseCase(planId)) {
                is ApiResponse.Success -> {
                    sendEvent(HomeEvent.OpenUrl(response.data.paymentUrl))
                }
                is ApiResponse.Error -> {
                    sendEvent(HomeEvent.ShowError(response.message))
                }
            }
        } catch (e: Exception) {
            sendEvent(HomeEvent.ShowError(e.message ?: "خطا در برقراری ارتباط"))
        } finally {
            emit(HomeState.PartialState.IsLoading(false))
        }
    }

    private fun calculateQueueTimes(appointments: List<AppointmentWithDetails>): List<QueueItem> {
        var currentTime = DateTimeUtils.systemCurrentMilliseconds()
        
        return appointments.map { item ->
            val appointment = item.appointment
            val visitor = item.visitor
            
            val startTime = if (appointment.appointmentDate > currentTime) {
                appointment.appointmentDate
            } else {
                currentTime
            }
            val duration = (appointment.serviceDuration ?: 15) * 60 * 1000L // default 15 mins
            val endTime = startTime + duration
            
            currentTime = endTime 
            
            QueueItem(
                appointment = appointment,
                visitorName = visitor.fullName,
                visitorPhone = visitor.phoneNumber,
                estimatedStartTime = startTime,
                estimatedEndTime = endTime
            )
        }
    }

    private fun removeAppointment(appointmentId: Long): Flow<HomeState.PartialState> = flow {
        removeAppointmentUseCase(appointmentId)
        emitAll(refreshQueueAndStats())
    }

    private fun markCompleted(appointmentId: Long): Flow<HomeState.PartialState> = flow {
        markAppointmentCompletedUseCase(appointmentId)
        emitAll(refreshQueueAndStats())
    }

    private fun markNoShow(appointmentId: Long): Flow<HomeState.PartialState> = flow {
        markAppointmentNoShowUseCase(appointmentId)
        emitAll(refreshQueueAndStats())
    }

    private fun sendMessage(appointmentId: Long, type: String, content: String, businessTitle: String): Flow<HomeState.PartialState> = flow {
        sendMessageUseCase(appointmentId, type, content, businessTitle)
        emitAll(refreshQueueAndStats())
    }
}
