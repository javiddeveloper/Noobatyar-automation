package xyz.sattar.javid.proqueue.feature.forgetPassword.sendOTP

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.usecase.user.SendOTPUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.VerifyOTPUseCase
import xyz.sattar.javid.proqueue.feature.forgetPassword.sendOTP.SendOTPState.PartialState.*
import xyz.sattar.javid.proqueue.core.network.ApiResponse

class SendOTPViewModel(
    private val sendOTPUseCase: SendOTPUseCase,
    private val verifyOTPUseCase: VerifyOTPUseCase
) : BaseViewModel<SendOTPState, SendOTPState.PartialState, SendOTPEvent, SendOTPIntent>(
    initialState = SendOTPState()
) {

    override fun handleIntent(intent: SendOTPIntent): Flow<SendOTPState.PartialState> {
        return when (intent) {
            is SendOTPIntent.PhoneChanged -> flow {
                emit(PhoneChanged(intent.phone))
            }

            is SendOTPIntent.OTPChanged -> flow {
                emit(OTPChanged(intent.otp))
                if (intent.otp.length == 6) {
                    emitAll(onValidateOTP(intent.phone, intent.otp))
                }
            }

            is SendOTPIntent.SendOTPAgain -> onSendOTPAgain(intent.phone)
            is SendOTPIntent.StartTimer -> startTimer(intent.expiresIn)
            SendOTPIntent.BackPress -> {
                sendEvent(SendOTPEvent.NavigateToLogin)
                flow {}
            }
        }
    }

    private fun onSendOTPAgain(phone: String): Flow<SendOTPState.PartialState> = flow {
        emit(IsLoading(true))
        when (val response = sendOTPUseCase.invoke(phone)) {
            is ApiResponse.Success -> {
                emit(OTPSent(response.data.expiresIn))
                emitAll(startTimer(response.data.expiresIn))
                emit(IsLoading(false))
            }
            is ApiResponse.Error -> {
                emit(SendOTPError(response.message))
                emit(IsLoading(false))
            }
        }
    }

    private fun onValidateOTP(phone: String, code: String): Flow<SendOTPState.PartialState> = flow {
        emit(IsLoading(true))
        when (val response = verifyOTPUseCase.invoke(phone, code)) {
            is ApiResponse.Success -> {
                emit(VerifySent(response.data.resetToken))
                sendEvent(SendOTPEvent.NavigateToResetPassword(phone, response.data.resetToken))
                emit(IsLoading(false))
            }
            is ApiResponse.Error -> {
                emit(SendOTPError(response.message))
                emit(IsLoading(false))
            }
        }
    }

    private fun startTimer(seconds: Int): Flow<SendOTPState.PartialState> = flow {
        var remaining = seconds
        while (remaining > 0) {
            emit(TimerTick(remaining))
            delay(1000)
            remaining--
        }
        emit(TimerFinished)
    }

    override fun reduceState(
        currentState: SendOTPState,
        partialState: SendOTPState.PartialState
    ): SendOTPState {
        return when (partialState) {
            is IsLoading -> currentState.copy(
                isLoading = partialState.isLoading,
                phoneError = null
            )

            is PhoneChanged -> currentState.copy(phone = partialState.phone, phoneError = null)
            is OTPChanged -> currentState.copy(otp = partialState.otp, otpError = null)
            is ValidationError -> currentState.copy(
                phoneError = partialState.phoneError,
                otpError = partialState.otpError,
                isLoading = false
            )

            is SendOTPError -> currentState.copy(
                otpError = partialState.message,
                isLoading = false
            )

            is OTPSent -> currentState.copy(
                remainingTime = partialState.expiresIn,
                canResend = false,
                otp = "",
                otpError = null
            )

            is VerifySent -> currentState.copy(
                resetToken = partialState.resetToken,
                isLoading = false
            )

            is TimerTick -> currentState.copy(remainingTime = partialState.seconds)
            is TimerFinished -> currentState.copy(remainingTime = 0, canResend = true)
        }
    }

    override fun createErrorState(message: String): SendOTPState.PartialState =
        SendOTPError(message)
}
