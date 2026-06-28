package xyz.sattar.javid.proqueue.feature.visitorSelection

sealed interface VisitorSelectionEvent {
    data class NavigateToCreateAppointment(val visitorId: Long) : VisitorSelectionEvent
    data object NavigateToCreateVisitor : VisitorSelectionEvent
    data class NavigateToEditVisitor(val visitorId: Long) : VisitorSelectionEvent
    data object NavigateBack : VisitorSelectionEvent
}
