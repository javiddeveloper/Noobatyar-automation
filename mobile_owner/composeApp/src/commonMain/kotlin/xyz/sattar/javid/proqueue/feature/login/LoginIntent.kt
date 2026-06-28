package xyz.sattar.javid.proqueue.feature.login

sealed interface LoginIntent {
    data class PhoneChanged(val phone: String) : LoginIntent
    data class PasswordChanged(val password: String) : LoginIntent
    data object Submit : LoginIntent
    data class ForgetPassword(val phone: String)  : LoginIntent
    data object Register : LoginIntent
}
