package xyz.sattar.javid.proqueue.feature.profile

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.usecase.UserLogoutUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.ClearTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetCurrentUserUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetMySubscriptionUseCase

class UserViewModel(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getMySubscriptionUseCase: GetMySubscriptionUseCase,
    private val userLogoutUseCase: UserLogoutUseCase,
    private val clearTokenUseCase: ClearTokenUseCase
) : BaseViewModel<UserState, UserState.PartialState, UserEvent, UserIntent>(
    initialState = UserState()
) {
    init {
        sendIntent(UserIntent.ObserveUser)
        sendIntent(UserIntent.LoadProfile)
    }

    override fun handleIntent(intent: UserIntent): Flow<UserState.PartialState> {
        return when (intent) {
            UserIntent.ObserveUser -> observeCurrentUser()
            UserIntent.LoadProfile -> loadSubscription()
            UserIntent.Logout -> logout()
        }
    }

    private fun observeCurrentUser(): Flow<UserState.PartialState> {
        return getCurrentUserUseCase().map { user ->
            if (user != null) {
                UserState.PartialState.UserProfile(user.name, user.phone)
            } else {
                UserState.PartialState.IsLoading(false)
            }
        }
    }

    override fun reduceState(currentState: UserState, partialState: UserState.PartialState): UserState {
        return when (partialState) {
            is UserState.PartialState.IsLoading -> currentState.copy(isLoading = partialState.isLoading)
            is UserState.PartialState.UserProfile -> currentState.copy(
                userName = partialState.name,
                userNumber = partialState.phone,
                isLoading = false
            )
            is UserState.PartialState.Subscription -> currentState.copy(
                subscription = partialState.subscription,
                isLoading = false
            )
            is UserState.PartialState.Error -> currentState.copy(error = partialState.message, isLoading = false)
        }
    }

    override fun createErrorState(message: String): UserState.PartialState = UserState.PartialState.Error(message)

    private fun loadSubscription(): Flow<UserState.PartialState> = flow {
        emit(UserState.PartialState.IsLoading(true))
        try {
            when (val subResponse = getMySubscriptionUseCase()) {
                is ApiResponse.Success -> {
                    emit(UserState.PartialState.Subscription(subResponse.data))
                }
                is ApiResponse.Error -> {
                    // Fail silently
                }
            }
        } catch (e: Exception) {
            // Error handling
        } finally {
            emit(UserState.PartialState.IsLoading(false))
        }
    }

    private fun logout(): Flow<UserState.PartialState> = flow {
        emit(UserState.PartialState.IsLoading(true))
        try {
            userLogoutUseCase()
        } catch (e: Exception) {
        } finally {
            clearTokenUseCase()
            BusinessStateHolder.clearBusiness()
            PreferencesManager.setDefaultBusinessId(null)
            emit(UserState.PartialState.IsLoading(false))
            sendEvent(UserEvent.LogoutSuccess)
        }
    }
}
