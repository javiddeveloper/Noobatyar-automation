package xyz.sattar.javid.proqueue.feature.calendar

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails

@Immutable
data class CalendarState(
    val isLoading: Boolean = false,
    val selectedDate: Long = DateTimeUtils.systemCurrentMilliseconds(),
    val appointments: List<AppointmentWithDetails> = emptyList(),
    val error: String? = null
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class UpdateSelectedDate(val date: Long) : PartialState()
        data class LoadAppointments(val appointments: List<AppointmentWithDetails>) : PartialState()
        data class ShowError(val error: String) : PartialState()
    }
}
