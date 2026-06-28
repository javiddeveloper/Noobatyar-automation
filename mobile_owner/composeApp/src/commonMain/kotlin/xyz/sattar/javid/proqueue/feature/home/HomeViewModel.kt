package xyz.sattar.javid.proqueue.feature.home

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.usecase.GetTodayStatsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetWaitingQueueUseCase
import xyz.sattar.javid.proqueue.domain.usecase.MarkAppointmentCompletedUseCase
import xyz.sattar.javid.proqueue.domain.usecase.MarkAppointmentNoShowUseCase
import xyz.sattar.javid.proqueue.domain.usecase.RemoveAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SendMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SyncAppointmentsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetPlansUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.CreatePaymentUseCase
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


class HomeViewModel(
    private val getWaitingQueueUseCase: GetWaitingQueueUseCase,
    private val getTodayStatsUseCase: GetTodayStatsUseCase,
    private val removeAppointmentUseCase: RemoveAppointmentUseCase,
    private val markAppointmentCompletedUseCase: MarkAppointmentCompletedUseCase,
    private val markAppointmentNoShowUseCase: MarkAppointmentNoShowUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val getPlansUseCase: GetPlansUseCase,
    private val createPaymentUseCase: CreatePaymentUseCase,
    private val syncAppointmentsUseCase: SyncAppointmentsUseCase
) : BaseViewModel<HomeState, HomeState.PartialState, HomeEvent, HomeIntent>(
    initialState = HomeState()
) {
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
            is HomeIntent.RemoveAppointment -> removeAppointment(intent.appointmentId)
            is HomeIntent.MarkAppointmentCompleted -> markCompleted(intent.appointmentId)
            is HomeIntent.MarkAppointmentNoShow -> markNoShow(intent.appointmentId)
            is HomeIntent.SendMessage -> sendMessage(intent.appointmentId, intent.type, intent.content, intent.businessTitle)
            is HomeIntent.PurchasePlan -> purchasePlan(intent.planId)
        }
    }

    override fun reduceState(
        currentState: HomeState,
        partialState: HomeState.PartialState
    ): HomeState {
        return when (partialState) {
            is HomeState.PartialState.IsLoading ->
                currentState.copy(isLoading = partialState.isLoading)
            is HomeState.PartialState.ShowMessage ->
                currentState.copy(message = partialState.message, isLoading = false)
            is HomeState.PartialState.LoadBusinessName ->
                currentState.copy(business = partialState.business, isLoading = false)
            is HomeState.PartialState.LoadQueue ->
                currentState.copy(queue = partialState.queue, isLoading = false)
            is HomeState.PartialState.LoadStats ->
                currentState.copy(stats = partialState.stats, isLoading = false)
            is HomeState.PartialState.LoadPlans ->
                currentState.copy(plans = partialState.plans)

            is HomeState.PartialState.ShowPaymentResult ->
                currentState.copy(paymentResult = partialState.info)
        }
    }

    override fun createErrorState(message: String): HomeState.PartialState =
        HomeState.PartialState.ShowMessage(message)

    private fun loadData(): Flow<HomeState.PartialState> = flow {
        emit(HomeState.PartialState.IsLoading(true))
        val business = BusinessStateHolder.selectedBusiness.value
        emit(HomeState.PartialState.LoadBusinessName(business))

        // Load Plans
        try {
            when (val plansResponse = getPlansUseCase()) {
                is ApiResponse.Success -> {
                    emit(HomeState.PartialState.LoadPlans(plansResponse.data))
                }
                is ApiResponse.Error -> {}
            }
        } catch (e: Exception) {}

        if (business != null) {
            try {
                // Sync Data
                @OptIn(ExperimentalTime::class)
                val today = Clock.System.now().toEpochMilliseconds()
                syncAppointmentsUseCase(business.id, date = today)

                // Load Queue
                val queue = getWaitingQueueUseCase(business.id, today)
                val queueItems = calculateQueueTimes(queue)
                emit(HomeState.PartialState.LoadQueue(queueItems))

                // Load Stats
                val stats = getTodayStatsUseCase(business.id)
                 emit(HomeState.PartialState.LoadStats(stats))
            } catch (e: Exception) {
                emit(HomeState.PartialState.ShowMessage(e.message ?: "Error loading data"))
            }
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
        emitAll(loadData())
    }

    private fun markCompleted(appointmentId: Long): Flow<HomeState.PartialState> = flow {
        markAppointmentCompletedUseCase(appointmentId)
        emitAll(loadData())
    }

    private fun markNoShow(appointmentId: Long): Flow<HomeState.PartialState> = flow {
        markAppointmentNoShowUseCase(appointmentId)
        emitAll(loadData())
    }

    private fun sendMessage(appointmentId: Long, type: String, content: String, businessTitle: String): Flow<HomeState.PartialState> = flow {
        sendMessageUseCase(appointmentId, type, content, businessTitle)
        emitAll(loadData())
    }
}
