package xyz.sattar.javid.proqueue.feature.register

sealed interface RegisterEvent {
    data object NavigateToHome : RegisterEvent
    data class ShowToast(val message: String) : RegisterEvent
    data object BackPress : RegisterEvent
}
