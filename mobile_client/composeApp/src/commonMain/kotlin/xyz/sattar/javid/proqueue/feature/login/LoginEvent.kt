package xyz.sattar.javid.proqueue.feature.login

sealed interface LoginEvent {
    data object NavigateToHome : LoginEvent
    data class ShowToast(val message: String) : LoginEvent
}
