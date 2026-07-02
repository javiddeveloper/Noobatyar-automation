package xyz.sattar.javid.proqueue.feature.login

sealed interface LoginIntent {
    data class PhoneChanged(val phone: String) : LoginIntent
    data class OtpChanged(val otp: String) : LoginIntent
    data class NameChanged(val name: String) : LoginIntent
    data object SubmitPhone : LoginIntent
    data object SubmitOtp : LoginIntent
    data object SubmitName : LoginIntent
    data object ChangePhone : LoginIntent // Go back to step 1
    data object ResendOtp : LoginIntent
    data class AutoFillOtp(val otp: String) : LoginIntent
}
