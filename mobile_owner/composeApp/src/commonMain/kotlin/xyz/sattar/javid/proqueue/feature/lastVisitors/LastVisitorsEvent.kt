package xyz.sattar.javid.proqueue.feature.lastVisitors

sealed interface LastVisitorsEvent {
    data object NavigateToCreateAppointment : LastVisitorsEvent
    data class NavigateToEditAppointment(val appointmentId: Long) : LastVisitorsEvent
    data class NavigateToVisitorDetails(val visitorId: Long) : LastVisitorsEvent
}

