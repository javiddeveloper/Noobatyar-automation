package xyz.sattar.javid.proqueue.feature.forgetPassword.sendOTP

import androidx.compose.runtime.Immutable

sealed interface SendOTPEvent {
    data class NavigateToResetPassword(val phone: String, val resetToken: String) : SendOTPEvent
    data object NavigateToLogin : SendOTPEvent
    data class ShowToast(val message: String) : SendOTPEvent
}

sealed interface SendOTPIntent {
    data class PhoneChanged(val phone: String) : SendOTPIntent
    data class OTPChanged(val otp: String,val phone: String) : SendOTPIntent
    data class SendOTPAgain(val phone: String) : SendOTPIntent
    data class StartTimer(val expiresIn: Int) : SendOTPIntent
    data object BackPress : SendOTPIntent
}

@Immutable
data class SendOTPState(
    val isLoading: Boolean = false,
    val phone: String = "",
    val otp: String = "",
    val resetToken: String? = null,
    val phoneError: String? = null,
    val otpError: String? = null,
    val remainingTime: Int = 0,
    val canResend: Boolean = false,
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class PhoneChanged(val phone: String) : PartialState()
        data class OTPChanged(val otp: String) : PartialState()
        data class SendOTPError(val message: String) : PartialState()
        data class ValidationError(
            val phoneError: String?,
            val otpError: String?,
        ) : PartialState()
        data class OTPSent(val expiresIn: Int) : PartialState()
        data class TimerTick(val seconds: Int) : PartialState()
        data object TimerFinished : PartialState()
        data class VerifySent(val resetToken: String) : PartialState()
    }
}
