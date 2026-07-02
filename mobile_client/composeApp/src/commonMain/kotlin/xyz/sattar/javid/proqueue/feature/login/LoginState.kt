package xyz.sattar.javid.proqueue.feature.login

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.domain.model.user.User

@Immutable
data class LoginState(
    val isLoading: Boolean = false,
    val phone: String = "",
    val password: String = "",
    val phoneError: String? = null,
    val passwordError: String? = null,
    val loginError: String? = null,
    val loggedInUser: User? = null
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class PhoneChanged(val phone: String) : PartialState()
        data class PasswordChanged(val password: String) : PartialState()
        data class ValidationError(
            val phoneError: String? = null,
            val passwordError: String? = null,
        ) : PartialState()
        data class LoginError(val message: String) : PartialState()
        data class LoginSuccess(val user: User) : PartialState()
    }
}
