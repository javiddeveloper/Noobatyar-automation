package xyz.sattar.javid.proqueue.feature.profile

import androidx.compose.runtime.Immutable


@Immutable
data class UserState(
    val isLoading: Boolean = false,
    val userName: String? = null,
    val userNumber: String? = null,
    val error: String? = null
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class UserProfile(val name: String, val phone: String) : PartialState()
        data class Error(val message: String) : PartialState()
    }
}

sealed interface UserIntent {
    data object ObserveUser : UserIntent
    data object Logout : UserIntent
}

sealed interface UserEvent {
    data object LogoutSuccess : UserEvent
}
