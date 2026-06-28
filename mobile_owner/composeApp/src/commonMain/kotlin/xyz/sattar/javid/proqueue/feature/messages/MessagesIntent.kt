package xyz.sattar.javid.proqueue.feature.messages

sealed interface MessagesIntent {
    data object Load : MessagesIntent
    data class UpdateTemplate(val text: String) : MessagesIntent
    data class InsertToken(val token: String) : MessagesIntent
    data class SetReminder(val minutes: Int) : MessagesIntent
    data class ApplyReadyTemplate(val text: String) : MessagesIntent
    data object Save : MessagesIntent
}

