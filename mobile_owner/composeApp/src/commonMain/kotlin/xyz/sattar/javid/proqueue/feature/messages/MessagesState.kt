package xyz.sattar.javid.proqueue.feature.messages

import xyz.sattar.javid.proqueue.domain.model.business.ReminderDelivery

data class MessagesState(
    val businessId: Long? = null,
    val businessTitle: String = "",
    val template: String = "",
    val preview: String = "",
    val reminderMinutes: Int = 20,
    val isLoading: Boolean = false,
    val message: String? = null,
    val readyTemplates: List<String> = emptyList(),
    /** [ReminderDelivery] value currently chosen for this business. */
    val reminderDelivery: String = ReminderDelivery.MANUAL.value,
    /** PANEL is only selectable with the `auto_reminder_sms` entitlement. */
    val canUsePanelDelivery: Boolean = false
) {
    sealed interface PartialState {
        data class IsLoading(val loading: Boolean) : PartialState
        data class ShowMessage(val text: String?) : PartialState
        data class ApplyBusiness(val id: Long, val title: String) : PartialState
        data class ApplyTemplate(val text: String) : PartialState
        data class ApplyPreview(val text: String) : PartialState
        data class SetReminder(val minutes: Int) : PartialState
        data class LoadReadyTemplates(val list: List<String>) : PartialState
        data class SetDelivery(val delivery: String) : PartialState
        data class SetPanelAllowed(val allowed: Boolean) : PartialState
    }
}

/** Lead times offered for the reminder, in minutes before the appointment. */
val reminderLeadTimeOptions: List<Int> = listOf(10, 15, 20, 30, 45, 60, 120)
