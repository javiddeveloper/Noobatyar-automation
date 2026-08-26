package xyz.sattar.javid.proqueue.data.localDataSource.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.sattar.javid.proqueue.domain.model.user.Subscription
import xyz.sattar.javid.proqueue.domain.model.user.User

/**
 * Adapter that sits between [UserDao] (Room, speaks `UserEntity` /
 * `SubscriptionEntity`) and [UserLocalSource] (the interface the repository
 * actually depends on, speaks [User] / [Subscription]). See
 * docs/OWNER_WEB_PLAN.md section 5.
 */
class RoomUserLocalSource(
    private val dao: UserDao
) : UserLocalSource {
    override suspend fun insertUser(user: User) =
        dao.insertUser(user.toEntity())

    override fun getUserById(id: Int): Flow<User?> =
        dao.getUserById(id).map { it?.toDomain() }

    override fun getCurrentUser(): Flow<User?> =
        dao.getCurrentUser().map { it?.toDomain() }

    override suspend fun insertSubscription(subscription: Subscription) =
        dao.insertSubscription(subscription.toEntity())

    override fun getActiveSubscription(): Flow<Subscription?> =
        dao.getActiveSubscription().map { it?.toDomain() }

    override suspend fun clearUser() =
        dao.clearUser()

    override suspend fun clearSubscription() =
        dao.clearSubscription()
}
