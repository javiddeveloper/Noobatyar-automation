package xyz.sattar.javid.proqueue.feature.messages

data class MessagesState(
    val businessId: Long? = null,
    val businessTitle: String = "",
    val template: String = "",
    val preview: String = "",
    val reminderMinutes: Int = 20,
    val isLoading: Boolean = false,
    val message: String? = null,
    val readyTemplates: List<String> = emptyList()
) {
    sealed interface PartialState {
        data class IsLoading(val loading: Boolean) : PartialState
        data class ShowMessage(val text: String?) : PartialState
        data class ApplyBusiness(val id: Long, val title: String) : PartialState
        data class ApplyTemplate(val text: String) : PartialState
        data class ApplyPreview(val text: String) : PartialState
        data class SetReminder(val minutes: Int) : PartialState
        data class LoadReadyTemplates(val list: List<String>) : PartialState
    }
}

