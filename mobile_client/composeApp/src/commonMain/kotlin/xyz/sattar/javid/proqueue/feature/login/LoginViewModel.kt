package xyz.sattar.javid.proqueue.feature.login

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.usecase.user.SendAuthOTPUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.VerifyAuthOTPUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.RegisterUseCase
import xyz.sattar.javid.proqueue.feature.login.LoginState.PartialState.*

class LoginViewModel(
    private val sendOTPUseCase: SendAuthOTPUseCase,
    private val verifyOTPUseCase: VerifyAuthOTPUseCase,
    private val registerUseCase: RegisterUseCase,
    private val smsRetrieverManager: xyz.sattar.javid.proqueue.core.utils.SmsRetrieverManager
) : BaseViewModel<LoginState, LoginState.PartialState, LoginEvent, LoginIntent>(
    initialState = LoginState()
) {

    override fun handleIntent(intent: LoginIntent): Flow<LoginState.PartialState> {
        return when (intent) {
            is LoginIntent.PhoneChanged -> flow { emit(PhoneChanged(intent.phone)) }
            is LoginIntent.OtpChanged -> flow { emit(OtpChanged(intent.otp)) }
            is LoginIntent.NameChanged -> flow { emit(NameChanged(intent.name)) }
            is LoginIntent.AutoFillOtp -> flow { 
                emit(OtpChanged(intent.otp)) 
                sendEvent(LoginEvent.ShowToast("کد به صورت خودکار خوانده شد"))
            }
            LoginIntent.SubmitPhone -> onSubmitPhone()
            LoginIntent.SubmitOtp -> onSubmitOtp()
            LoginIntent.SubmitName -> onSubmitName()
            LoginIntent.ChangePhone -> flow { emit(StepChanged(LoginStep.PHONE)) }
            LoginIntent.ResendOtp -> onSubmitPhone()
        }
    }

    private fun onSubmitPhone(): Flow<LoginState.PartialState> = flow {
        val phone = uiState.value.phone
        if (phone.isEmpty() || phone.length != 11 || !phone.startsWith("09")) {
            emit(ValidationError(phoneError = "شماره موبایل معتبر نیست"))
            return@flow
        }

        emit(IsLoading(true))
        when (val response = sendOTPUseCase(phone)) {
            is ApiResponse.Success -> {
                emit(StepChanged(LoginStep.OTP))
                smsRetrieverManager.startListening { code ->
                    sendIntent(LoginIntent.AutoFillOtp(code))
                }
                emit(IsLoading(false))
            }
            is ApiResponse.Error -> {
                sendEvent(LoginEvent.ShowToast(response.message))
                emit(LoginError(response.message))
                emit(IsLoading(false))
            }
        }
    }

    private fun onSubmitOtp(): Flow<LoginState.PartialState> = flow {
        val phone = uiState.value.phone
        val code = uiState.value.otpCode

        if (code.isEmpty() || code.length < 4) {
            emit(ValidationError(otpError = "کد تأیید را وارد کنید"))
            return@flow
        }

        emit(IsLoading(true))
        when (val response = verifyOTPUseCase(phone, code)) {
            is ApiResponse.Success -> {
                if (response.data.isRegistered) {
                    sendEvent(LoginEvent.NavigateToHome)
                } else {
                    emit(RegisterTokenReceived(response.data.registerToken, response.data.expiresIn))
                    emit(StepChanged(LoginStep.REGISTER_NAME))
                }
                emit(IsLoading(false))
            }
            is ApiResponse.Error -> {
                sendEvent(LoginEvent.ShowToast(response.message))
                emit(LoginError(response.message))
                emit(IsLoading(false))
            }
        }
    }

    private fun onSubmitName(): Flow<LoginState.PartialState> = flow {
        val phone = uiState.value.phone
        val token = uiState.value.registerToken ?: ""
        val name = uiState.value.name

        if (name.isEmpty() || name.length < 2) {
            emit(ValidationError(nameError = "نام را وارد کنید"))
            return@flow
        }

        emit(IsLoading(true))
        when (val response = registerUseCase(phone, token, name)) {
            is ApiResponse.Success -> {
                emit(LoginSuccess(response.data))
                sendEvent(LoginEvent.NavigateToHome)
                emit(IsLoading(false))
            }
            is ApiResponse.Error -> {
                sendEvent(LoginEvent.ShowToast(response.message))
                emit(LoginError(response.message))
                emit(IsLoading(false))
            }
        }
    }

    override fun reduceState(
        currentState: LoginState,
        partialState: LoginState.PartialState
    ): LoginState {
        return when (partialState) {
            is IsLoading -> currentState.copy(isLoading = partialState.isLoading, loginError = null)
            is StepChanged -> currentState.copy(step = partialState.step, loginError = null, phoneError = null, otpError = null, nameError = null)
            is PhoneChanged -> currentState.copy(phone = partialState.phone, loginError = null, phoneError = null)
            is OtpChanged -> currentState.copy(otpCode = partialState.otp, loginError = null, otpError = null)
            is NameChanged -> currentState.copy(name = partialState.name, loginError = null, nameError = null)
            is RegisterTokenReceived -> currentState.copy(registerToken = partialState.token, expiresIn = partialState.expiresIn)
            is ValidationError -> currentState.copy(
                phoneError = partialState.phoneError,
                otpError = partialState.otpError,
                nameError = partialState.nameError,
                isLoading = false
            )
            is LoginError -> currentState.copy(loginError = partialState.message, isLoading = false)
            is LoginSuccess -> currentState.copy(loggedInUser = partialState.user, isLoading = false)
        }
    }

    override fun createErrorState(message: String): LoginState.PartialState =
        LoginError(message)

    override fun onCleared() {
        super.onCleared()
        smsRetrieverManager.stopListening()
    }
}
