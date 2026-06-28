package xyz.sattar.javid.proqueue.feature.forgetPassword.resetPassword

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.usecase.user.ResetPasswordUseCase
import xyz.sattar.javid.proqueue.feature.forgetPassword.resetPassword.ResetPasswordState.PartialState.*

class ResetPasswordViewModel(
    private val resetPasswordUseCase: ResetPasswordUseCase
) : BaseViewModel<ResetPasswordState, ResetPasswordState.PartialState, ResetPasswordEvent, ResetPasswordIntent>(
    initialState = ResetPasswordState()
) {
    private var phone: String = ""
    private var resetToken: String = ""

    fun initialize(phone: String, resetToken: String) {
        this.phone = phone
        this.resetToken = resetToken
    }

    override fun handleIntent(intent: ResetPasswordIntent): Flow<ResetPasswordState.PartialState> {
        return when (intent) {
            is ResetPasswordIntent.NewPasswordChanged -> flow {
                emit(NewPasswordChanged(intent.password))
            }
            is ResetPasswordIntent.ConfirmPasswordChanged -> flow {
                emit(ConfirmPasswordChanged(intent.password))
            }
            ResetPasswordIntent.Submit -> submitResetPassword()
            ResetPasswordIntent.NavigateToLogin -> {
                sendEvent(ResetPasswordEvent.NavigateToLogin)
                flow {}
            }
        }
    }

    override fun reduceState(
        currentState: ResetPasswordState,
        partialState: ResetPasswordState.PartialState
    ): ResetPasswordState {
        return when (partialState) {
            is IsLoading ->
                currentState.copy(isLoading = partialState.isLoading, errorMessage = null)
            is NewPasswordChanged ->
                currentState.copy(newPassword = partialState.password, newPasswordError = null)
            is ConfirmPasswordChanged ->
                currentState.copy(confirmPassword = partialState.password, confirmPasswordError = null)
            is ValidationError ->
                currentState.copy(
                    newPasswordError = partialState.newPasswordError,
                    confirmPasswordError = partialState.confirmPasswordError,
                    isLoading = false
                )
            is ResetPasswordError ->
                currentState.copy(errorMessage = partialState.message, isLoading = false)
            ResetPasswordSuccess ->
                currentState.copy(isLoading = false)
        }
    }

    override fun createErrorState(message: String): ResetPasswordState.PartialState =
        ResetPasswordError(message)

    private fun submitResetPassword(): Flow<ResetPasswordState.PartialState> = flow {
        val currentState = uiState.value

        val newPasswordError = when {
            currentState.newPassword.isBlank() -> "رمز عبور جدید را وارد کنید"
            currentState.newPassword.length < 5 -> "رمز عبور باید حداقل ۵ کاراکتر باشد"
            else -> null
        }
        val confirmPasswordError = when {
            currentState.confirmPassword.isBlank() -> "تکرار رمز عبور را وارد کنید"
            currentState.newPassword != currentState.confirmPassword -> "رمز عبور و تکرار آن یکسان نیستند"
            else -> null
        }

        if (newPasswordError != null || confirmPasswordError != null) {
            emit(ValidationError(newPasswordError, confirmPasswordError))
            return@flow
        }

        emit(IsLoading(true))

        when (val response = resetPasswordUseCase(
            phone = phone,
            resetToken = resetToken,
            newPassword = currentState.newPassword
        )) {
            is ApiResponse.Success -> {
                emit(ResetPasswordSuccess)
                sendEvent(ResetPasswordEvent.ShowToast("رمز عبور با موفقیت تغییر کرد"))
                sendEvent(ResetPasswordEvent.NavigateToLogin)
            }
            is ApiResponse.Error -> {
                emit(ResetPasswordError(response.message))
                sendEvent(ResetPasswordEvent.ShowToast(response.message))
            }
        }
    }
}
