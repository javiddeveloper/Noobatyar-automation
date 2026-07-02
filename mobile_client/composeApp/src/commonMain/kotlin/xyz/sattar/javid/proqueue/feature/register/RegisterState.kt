package xyz.sattar.javid.proqueue.feature.register

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.domain.model.user.User

@Immutable
data class RegisterState(
    val isLoading: Boolean = false,
    val phone: String = "",
    val password: String = "",
    val name: String = "",
    val phoneError: String? = null,
    val passwordError: String? = null,
    val nameError: String? = null,
    val errorMessage: String? = null,
    val registeredUser: User? = null
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class PhoneChanged(val phone: String) : PartialState()
        data class PasswordChanged(val password: String) : PartialState()
        data class NameChanged(val name: String) : PartialState()
        data class ValidationError(
            val phoneError: String?,
            val passwordError: String?,
            val nameError: String?
        ) : PartialState()
        data class RegisterError(val message: String) : PartialState()
        data class RegisterSuccess(val user: User) : PartialState()
    }
}
