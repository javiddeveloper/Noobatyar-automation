package xyz.sattar.javid.proqueue.feature.login

sealed interface LoginEvent {
    data object NavigateToHome : LoginEvent
    data object NavigateToRegister : LoginEvent
    data class ShowToast(val message: String) : LoginEvent
    data class NavigateToForgetPassword(val phone:String) : LoginEvent
}
