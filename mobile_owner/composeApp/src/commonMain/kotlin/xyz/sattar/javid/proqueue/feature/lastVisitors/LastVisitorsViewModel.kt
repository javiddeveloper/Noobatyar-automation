package xyz.sattar.javid.proqueue.feature.lastVisitors

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.domain.usecase.GenerateReminderMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.MarkAppointmentCompletedUseCase
import xyz.sattar.javid.proqueue.domain.usecase.MarkAppointmentNoShowUseCase
import xyz.sattar.javid.proqueue.domain.usecase.RemoveAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SendMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SyncAppointmentsUseCase
import kotlin.time.ExperimentalTime

class LastVisitorsViewModel(
    private val removeAppointmentUseCase: RemoveAppointmentUseCase,
    private val markAppointmentCompletedUseCase: MarkAppointmentCompletedUseCase,
    private val markAppointmentNoShowUseCase: MarkAppointmentNoShowUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val generateReminderMessageUseCase: GenerateReminderMessageUseCase,
    private val syncAppointmentsUseCase: SyncAppointmentsUseCase,
    private val appointmentRepository: AppointmentRepository
) : BaseViewModel<LastVisitorsState, LastVisitorsState.PartialState, LastVisitorsEvent, LastVisitorsIntent>(
    initialState = LastVisitorsState()
) {

    init {
        viewModelScope.launch {
            BusinessStateHolder.selectedBusiness.collectLatest {
                sendIntent(LastVisitorsIntent.LoadAppointments)
            }
        }
    }

    override fun handleIntent(intent: LastVisitorsIntent): Flow<LastVisitorsState.PartialState> {
        return when (intent) {
            LastVisitorsIntent.LoadAppointments -> loadAppointments()
            is LastVisitorsIntent.OnAppointmentOptionsClick -> flow {
                emit(LastVisitorsState.PartialState.ShowOptionsDialog(intent.appointmentId))
            }
            LastVisitorsIntent.OnCreateAppointmentClick -> sendEvent(LastVisitorsEvent.NavigateToCreateAppointment)
            is LastVisitorsIntent.OnEditAppointment -> {
                sendEvent(LastVisitorsEvent.NavigateToEditAppointment(intent.appointmentId))
            }
            is LastVisitorsIntent.OnDeleteAppointment -> deleteAppointment(intent.appointmentId)
            is LastVisitorsIntent.OnMarkCompleted -> markCompleted(intent.appointmentId)
            is LastVisitorsIntent.OnMarkNoShow -> markNoShow(intent.appointmentId)
            LastVisitorsIntent.DismissDialog -> flow {
                emit(LastVisitorsState.PartialState.ShowOptionsDialog(null))
            }
            is LastVisitorsIntent.OnTabSelected -> flow {
                emit(LastVisitorsState.PartialState.TabSelected(intent.index))
            }
            is LastVisitorsIntent.OnAppointmentClick -> {
                sendEvent(LastVisitorsEvent.NavigateToVisitorDetails(intent.visitorId))
            }
            is LastVisitorsIntent.OnFilterChanged -> flow {
                emit(LastVisitorsState.PartialState.UpdateFilter(intent.filter))
                emit(LastVisitorsState.PartialState.ShowFilterSheet(false))
                emitAll(loadAppointments(intent.filter))
            }
            is LastVisitorsIntent.ShowFilterSheet -> flow {
                emit(LastVisitorsState.PartialState.ShowFilterSheet(intent.show))
            }
            LastVisitorsIntent.ClearFilter -> flow {
                val defaultFilter = AppointmentFilter()
                emit(LastVisitorsState.PartialState.UpdateFilter(defaultFilter))
                emitAll(loadAppointments(defaultFilter))
            }
            is LastVisitorsIntent.OnSendMessage -> flow {
                try {
                    val success = sendMessageUseCase(
                        appointmentId = intent.appointmentId,
                        type = intent.type,
                        content = intent.content,
                        businessTitle = intent.businessTitle
                    )
                    if (!success) emit(LastVisitorsState.PartialState.ShowMessage("خطا در ثبت پیام"))
                } catch (e: Exception) {
                    emit(LastVisitorsState.PartialState.ShowMessage(e.message ?: "خطا در ثبت پیام"))
                }
            }
        }
    }

    override fun reduceState(
        currentState: LastVisitorsState,
        partialState: LastVisitorsState.PartialState
    ): LastVisitorsState {
        return when (partialState) {
            is LastVisitorsState.PartialState.IsLoading ->
                currentState.copy(isLoading = partialState.isLoading)
            is LastVisitorsState.PartialState.ShowMessage ->
                currentState.copy(message = partialState.message, isLoading = false)
            is LastVisitorsState.PartialState.LoadAppointments ->
                currentState.copy(
                    appointments = partialState.appointments,
                    totalCount = partialState.totalCount,
                    isLoading = false
                )
            is LastVisitorsState.PartialState.ShowOptionsDialog ->
                currentState.copy(
                    selectedAppointmentId = partialState.appointmentId,
                    showOptionsDialog = partialState.appointmentId != null
                )
            is LastVisitorsState.PartialState.TabSelected ->
                currentState.copy(selectedTab = partialState.index)
            is LastVisitorsState.PartialState.UpdateFilter ->
                currentState.copy(filter = partialState.filter)
            is LastVisitorsState.PartialState.ShowFilterSheet ->
                currentState.copy(showFilterSheet = partialState.show)
        }
    }

    override fun createErrorState(message: String): LastVisitorsState.PartialState =
        LastVisitorsState.PartialState.ShowMessage(message)

    fun generateReminderMessage(
        businessId: Long,
        businessTitle: String,
        businessAddress: String,
        visitorName: String,
        appointmentMillis: Long,
        reminderMinutes: String,
        serviceDuration: Int?
    ): String {
        return generateReminderMessageUseCase(
            businessId = businessId,
            businessTitle = businessTitle,
            businessAddress = businessAddress,
            visitorName = visitorName,
            appointmentMillis = appointmentMillis,
            reminderMinutes = reminderMinutes,
            serviceDuration = serviceDuration
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun loadAppointments(filter: AppointmentFilter = uiState.value.filter): Flow<LastVisitorsState.PartialState> = flow {
        emit(LastVisitorsState.PartialState.IsLoading(true))
        try {
            val business = BusinessStateHolder.selectedBusiness.value
            if (business != null) {
                // Sync with server using filters
                syncAppointmentsUseCase(
                    businessId = business.id,
                    status = filter.status,
                    date = filter.date,
                    dateFrom = filter.dateFrom,
                    dateTo = filter.dateTo,
                    ordering = filter.ordering
                )

                // Load from DB
                val appointments = appointmentRepository.getAllAppointmentsForBusiness(business.id)
                
                // Locally apply filter
                val filtered = appointments.filter { app ->
                    (filter.status == null || app.appointment.status == filter.status) &&
                    (filter.date == null || DateTimeUtils.isSameDay(app.appointment.appointmentDate, filter.date))
                }

                emit(
                    LastVisitorsState.PartialState.LoadAppointments(
                        appointments = filtered,
                        totalCount = filtered.size
                    )
                )
            } else {
                emit(LastVisitorsState.PartialState.ShowMessage("لطفاً ابتدا یک کسب‌وکار انتخاب کنید"))
            }
        } catch (e: Exception) {
            emit(LastVisitorsState.PartialState.ShowMessage(e.message ?: "خطا در بارگذاری نوبت‌ها"))
        } finally {
            emit(LastVisitorsState.PartialState.IsLoading(false))
        }
    }

    private fun deleteAppointment(appointmentId: Long): Flow<LastVisitorsState.PartialState> = flow {
        try {
            val success = removeAppointmentUseCase(appointmentId)
            if (success) {
                emit(LastVisitorsState.PartialState.ShowOptionsDialog(null))
                emitAll(loadAppointments())
            } else {
                emit(LastVisitorsState.PartialState.ShowMessage("خطا در حذف نوبت"))
            }
        } catch (e: Exception) {
            emit(LastVisitorsState.PartialState.ShowMessage(e.message ?: "خطا در حذف نوبت"))
        }
    }

    private fun markCompleted(appointmentId: Long): Flow<LastVisitorsState.PartialState> = flow {
        try {
            val success = markAppointmentCompletedUseCase(appointmentId)
            if (success) {
                emitAll(loadAppointments())
            } else {
                emit(LastVisitorsState.PartialState.ShowMessage("خطا در تکمیل نوبت"))
            }
        } catch (e: Exception) {
            emit(LastVisitorsState.PartialState.ShowMessage(e.message ?: "خطا در تکمیل نوبت"))
        }
    }

    private fun markNoShow(appointmentId: Long): Flow<LastVisitorsState.PartialState> = flow {
        try {
            val success = markAppointmentNoShowUseCase(appointmentId)
            if (success) {
                emitAll(loadAppointments())
            } else {
                emit(LastVisitorsState.PartialState.ShowMessage("خطا در ثبت عدم مراجعه"))
            }
        } catch (e: Exception) {
            emit(LastVisitorsState.PartialState.ShowMessage(e.message ?: "خطا در ثبت عدم مراجعه"))
        }
    }
}
