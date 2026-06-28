package xyz.sattar.javid.proqueue.feature.calendar

sealed interface CalendarIntent {
    data class SelectDate(val date: Long) : CalendarIntent
    data object LoadData : CalendarIntent
    data class OnTimeSlotClick(val time: String) : CalendarIntent // Click on empty slot
    data class OnAppointmentClick(val appointmentId: Long) : CalendarIntent
    data object BackPress : CalendarIntent
}
