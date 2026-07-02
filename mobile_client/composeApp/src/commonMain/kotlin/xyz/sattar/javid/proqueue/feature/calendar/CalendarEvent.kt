package xyz.sattar.javid.proqueue.feature.calendar

sealed interface CalendarEvent {
    data object NavigateBack : CalendarEvent
    data class NavigateToCreateAppointment(val date: Long, val time: String) : CalendarEvent
    data class NavigateToAppointmentDetails(val appointmentId: Long) : CalendarEvent
}
