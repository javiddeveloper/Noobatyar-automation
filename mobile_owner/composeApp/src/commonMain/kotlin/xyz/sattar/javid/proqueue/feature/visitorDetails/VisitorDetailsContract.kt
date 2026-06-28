package xyz.sattar.javid.proqueue.feature.visitorDetails

import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.model.message.Message
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

data class VisitorDetailsState(
    val isLoading: Boolean = false,
    val visitor: Visitor? = null,
    val appointments: List<AppointmentWithDetails> = emptyList(),
    val messages: List<Message> = emptyList(),
    val message: String? = null
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class ShowMessage(val message: String) : PartialState()
        data class DetailsLoaded(
            val visitor: Visitor,
            val appointments: List<AppointmentWithDetails>,
            val messages: List<Message>
        ) : PartialState()
    }
}

sealed class VisitorDetailsIntent {
    data class LoadVisitorDetails(val visitorId: Long) : VisitorDetailsIntent()
    object BackPress : VisitorDetailsIntent()
    data class OnSendMessage(
        val appointmentId: Long,
        val type: String,
        val content: String,
        val businessTitle: String
    ) : VisitorDetailsIntent()
    data class DeleteMessage(val id: Long) : VisitorDetailsIntent()
}

sealed class VisitorDetailsEvent {
    object NavigateBack : VisitorDetailsEvent()
}
