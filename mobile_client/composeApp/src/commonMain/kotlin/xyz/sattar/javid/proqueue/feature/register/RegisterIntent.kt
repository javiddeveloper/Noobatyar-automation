package xyz.sattar.javid.proqueue.feature.register

sealed interface RegisterIntent {
    data class PhoneChanged(val phone: String) : RegisterIntent
    data class PasswordChanged(val password: String) : RegisterIntent
    data class NameChanged(val name: String) : RegisterIntent
    data object Submit : RegisterIntent
    data object BackPress : RegisterIntent
}
