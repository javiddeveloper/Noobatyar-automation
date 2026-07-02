package xyz.sattar.javid.proqueue.feature.login

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.domain.model.user.User

enum class LoginStep {
    PHONE,
    OTP,
    REGISTER_NAME
}

@Immutable
data class LoginState(
    val step: LoginStep = LoginStep.PHONE,
    val isLoading: Boolean = false,
    val phone: String = "",
    val otpCode: String = "",
    val name: String = "",
    val registerToken: String? = null,
    val expiresIn: Int? = null,
    val phoneError: String? = null,
    val otpError: String? = null,
    val nameError: String? = null,
    val loginError: String? = null,
    val loggedInUser: User? = null
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class StepChanged(val step: LoginStep) : PartialState()
        data class PhoneChanged(val phone: String) : PartialState()
        data class OtpChanged(val otp: String) : PartialState()
        data class NameChanged(val name: String) : PartialState()
        data class RegisterTokenReceived(val token: String?, val expiresIn: Int?) : PartialState()
        data class ValidationError(
            val phoneError: String? = null,
            val otpError: String? = null,
            val nameError: String? = null
        ) : PartialState()
        data class LoginError(val message: String) : PartialState()
        data class LoginSuccess(val user: User) : PartialState()
    }
}
