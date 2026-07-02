package xyz.sattar.javid.proqueue.feature.register

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.usecase.user.RegisterUseCase
import xyz.sattar.javid.proqueue.feature.register.RegisterState.PartialState.*

class RegisterViewModel(
    private val registerUseCase: RegisterUseCase
) : BaseViewModel<RegisterState, RegisterState.PartialState, RegisterEvent, RegisterIntent>(
    initialState = RegisterState()
) {

    override fun handleIntent(intent: RegisterIntent): Flow<RegisterState.PartialState> {
        return when (intent) {
            is RegisterIntent.NameChanged -> flow { emit(NameChanged(intent.name)) }
            is RegisterIntent.PhoneChanged -> flow { emit(PhoneChanged(intent.phone)) }
            is RegisterIntent.PasswordChanged -> flow { emit(PasswordChanged(intent.password)) }
            RegisterIntent.Submit -> onRegisterClicked()
            RegisterIntent.BackPress -> {
                sendEvent(RegisterEvent.BackPress)
                flow {}
            }
        }
    }

    private fun onRegisterClicked(): Flow<RegisterState.PartialState> = flow {
        val name = uiState.value.name
        val phone = uiState.value.phone
        val password = uiState.value.password

        if (name.isEmpty() || phone.isEmpty() || password.isEmpty()) {
            emit(
                ValidationError(
                    phoneError = if (phone.isEmpty()) "شماره همراه را وارد کنید" else null,
                    passwordError = if (password.isEmpty()) "رمز عبور را وارد کنید" else null,
                    nameError = if (name.isEmpty()) "نام را وارد کنید" else null
                )
            )
            return@flow
        }

        emit(IsLoading(true))
        when (val response = registerUseCase(phone, password, name)) {
            is ApiResponse.Success -> {
                emit(RegisterSuccess(response.data))
                sendEvent(RegisterEvent.NavigateToHome)
                emit(IsLoading(false))
            }
            is ApiResponse.Error -> {
                sendEvent(RegisterEvent.ShowToast(response.message))
                emit(RegisterError(response.message))
                emit(IsLoading(false))
            }
        }
    }

    override fun reduceState(
        currentState: RegisterState,
        partialState: RegisterState.PartialState
    ): RegisterState {
        return when (partialState) {
            is IsLoading -> currentState.copy(isLoading = partialState.isLoading, errorMessage = null)
            is NameChanged -> currentState.copy(name = partialState.name, nameError = null, errorMessage = null)
            is PhoneChanged -> currentState.copy(phone = partialState.phone, phoneError = null, errorMessage = null)
            is PasswordChanged -> currentState.copy(password = partialState.password, passwordError = null, errorMessage = null)
            is ValidationError -> currentState.copy(
                phoneError = partialState.phoneError,
                passwordError = partialState.passwordError,
                nameError = partialState.nameError,
                isLoading = false
            )
            is RegisterError -> currentState.copy(errorMessage = partialState.message, isLoading = false)
            is RegisterSuccess -> currentState.copy(registeredUser = partialState.user, isLoading = false)
        }
    }

    override fun createErrorState(message: String): RegisterState.PartialState =
        RegisterError(message)
}
