package xyz.sattar.javid.proqueue.feature.profile

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SubscriptionDto

@Immutable
data class UserState(
    val isLoading: Boolean = false,
    val userName: String? = null,
    val userNumber: String? = null,
    val subscription: SubscriptionDto? = null,
    val error: String? = null
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class UserProfile(val name: String, val phone: String) : PartialState()
        data class Subscription(val subscription: SubscriptionDto) : PartialState()
        data class Error(val message: String) : PartialState()
    }
}

sealed interface UserIntent {
    data object ObserveUser : UserIntent
    data object LoadProfile : UserIntent
    data object Logout : UserIntent
}

sealed interface UserEvent {
    data object LogoutSuccess : UserEvent
}
