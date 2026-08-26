package xyz.sattar.javid.proqueue.data.localDataSource.user

import kotlinx.coroutines.flow.Flow
import xyz.sattar.javid.proqueue.domain.model.user.Subscription
import xyz.sattar.javid.proqueue.domain.model.user.User

/**
 * Plain-interface indirection over [UserDao] so the repository layer does not
 * depend on Room directly (Room has no web target). See
 * [xyz.sattar.javid.proqueue.data.localDataSource.appointment.AppointmentLocalSource]
 * for the full rationale. Speaks in [User] / [Subscription] (domain models),
 * not the Room-annotated `UserEntity` / `SubscriptionEntity`.
 */
interface UserLocalSource {
    suspend fun insertUser(user: User)

    fun getUserById(id: Int): Flow<User?>

    fun getCurrentUser(): Flow<User?>

    suspend fun insertSubscription(subscription: Subscription)

    fun getActiveSubscription(): Flow<Subscription?>

    suspend fun clearUser()

    suspend fun clearSubscription()
}
