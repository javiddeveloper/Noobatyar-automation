package xyz.sattar.javid.proqueue.feature.forgetPassword.resetPassword

import androidx.compose.runtime.Immutable


@Immutable
data class ResetPasswordState(
    val isLoading: Boolean = false,
    val newPassword: String = "",
    val confirmPassword: String = "",
    val newPasswordError: String? = null,
    val confirmPasswordError: String? = null,
    val errorMessage: String? = null
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class NewPasswordChanged(val password: String) : PartialState()
        data class ConfirmPasswordChanged(val password: String) : PartialState()
        data class ValidationError(
            val newPasswordError: String?,
            val confirmPasswordError: String?
        ) : PartialState()

        data class ResetPasswordError(val message: String) : PartialState()
        data object ResetPasswordSuccess : PartialState()
    }
}

sealed interface ResetPasswordEvent {
    data object NavigateToLogin : ResetPasswordEvent
    data class ShowToast(val message: String) : ResetPasswordEvent
}

sealed interface ResetPasswordIntent {
    data class NewPasswordChanged(val password: String) : ResetPasswordIntent
    data class ConfirmPasswordChanged(val password: String) : ResetPasswordIntent
    data object Submit : ResetPasswordIntent
    data object NavigateToLogin : ResetPasswordIntent
}
