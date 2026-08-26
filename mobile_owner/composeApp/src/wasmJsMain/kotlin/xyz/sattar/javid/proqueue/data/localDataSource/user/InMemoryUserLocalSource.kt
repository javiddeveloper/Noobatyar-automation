package xyz.sattar.javid.proqueue.data.localDataSource.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import xyz.sattar.javid.proqueue.domain.model.user.Subscription
import xyz.sattar.javid.proqueue.domain.model.user.User

// See InMemoryBusinessLocalSource for why an in-memory map is an acceptable
// stand-in for Room here. Single-user per browser tab, so "current user" is
// simply "the last one inserted" — matching RoomUserLocalSource, which keeps
// exactly one row (id is always overwritten).
class InMemoryUserLocalSource : UserLocalSource {
    private val userState = MutableStateFlow<User?>(null)
    private val subscriptionState = MutableStateFlow<Subscription?>(null)

    override suspend fun insertUser(user: User) {
        userState.value = user
    }

    override fun getUserById(id: Int): Flow<User?> = userState

    override fun getCurrentUser(): Flow<User?> = userState

    override suspend fun insertSubscription(subscription: Subscription) {
        subscriptionState.value = subscription
    }

    override fun getActiveSubscription(): Flow<Subscription?> = subscriptionState

    override suspend fun clearUser() {
        userState.value = null
    }

    override suspend fun clearSubscription() {
        subscriptionState.value = null
    }
}
