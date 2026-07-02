package xyz.sattar.javid.proqueue.feature.calendar

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.usecase.GetAppointmentsForDateUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SyncAppointmentsUseCase
import xyz.sattar.javid.proqueue.feature.calendar.CalendarState.PartialState

class CalendarViewModel(
    private val getAppointmentsForDateUseCase: GetAppointmentsForDateUseCase,
    private val syncAppointmentsUseCase: SyncAppointmentsUseCase
) : BaseViewModel<CalendarState, PartialState, CalendarEvent, CalendarIntent>(
    initialState = CalendarState()
) {
    init {
        viewModelScope.launch {
            BusinessStateHolder.selectedBusiness.collectLatest {
                sendIntent(CalendarIntent.LoadData)
            }
        }
    }

    override fun handleIntent(intent: CalendarIntent): Flow<PartialState> {
        return when (intent) {
            is CalendarIntent.SelectDate -> selectDate(intent.date)
            CalendarIntent.LoadData -> loadAppointments(uiState.value.selectedDate)
            is CalendarIntent.OnTimeSlotClick -> flow {
                sendEvent(CalendarEvent.NavigateToCreateAppointment(uiState.value.selectedDate, intent.time))
            }
            is CalendarIntent.OnAppointmentClick -> flow {
                sendEvent(CalendarEvent.NavigateToAppointmentDetails(intent.appointmentId))
            }
            CalendarIntent.BackPress -> flow { sendEvent(CalendarEvent.NavigateBack) }
        }
    }

    override fun reduceState(currentState: CalendarState, partialState: PartialState): CalendarState {
        return when (partialState) {
            is PartialState.IsLoading -> currentState.copy(isLoading = partialState.isLoading)
            is PartialState.LoadAppointments -> currentState.copy(
                appointments = partialState.appointments,
                isLoading = false
            )
            is PartialState.UpdateSelectedDate -> currentState.copy(selectedDate = partialState.date)
            is PartialState.ShowError -> currentState.copy(error = partialState.error, isLoading = false)
        }
    }

    override fun createErrorState(message: String): PartialState = PartialState.ShowError(message)

    private fun selectDate(date: Long): Flow<PartialState> = flow {
        emit(PartialState.UpdateSelectedDate(date))
        emitAll(loadAppointments(date))
    }

    private fun loadAppointments(date: Long): Flow<PartialState> = flow {
        emit(PartialState.IsLoading(true))
        try {
            val business = BusinessStateHolder.selectedBusiness.value
            if (business != null) {
                // Sync Data
                syncAppointmentsUseCase(business.id, date = date)

                val appointments = getAppointmentsForDateUseCase(business.id, date)
                emit(PartialState.LoadAppointments(appointments))
            } else {
                emit(PartialState.ShowError("کسب‌وکاری انتخاب نشده است"))
            }
        } catch (e: Exception) {
            emit(PartialState.ShowError(e.message ?: "خطا در بارگذاری نوبت‌ها"))
        }
    }
}
