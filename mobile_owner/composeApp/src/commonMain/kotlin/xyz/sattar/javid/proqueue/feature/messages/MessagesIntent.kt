package xyz.sattar.javid.proqueue.feature.messages

sealed interface MessagesIntent {
    data object Load : MessagesIntent
    data class UpdateTemplate(val text: String) : MessagesIntent
    data class InsertToken(val token: String) : MessagesIntent
    data class SetReminder(val minutes: Int) : MessagesIntent
    data class ApplyReadyTemplate(val text: String) : MessagesIntent
    /** [xyz.sattar.javid.proqueue.domain.model.business.ReminderDelivery] value. */
    data class SetDelivery(val delivery: String) : MessagesIntent
    data object UpgradeForPanelDelivery : MessagesIntent
    data object Save : MessagesIntent
}

