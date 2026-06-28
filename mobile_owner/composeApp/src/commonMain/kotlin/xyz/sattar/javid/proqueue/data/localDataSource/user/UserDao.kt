package xyz.sattar.javid.proqueue.data.localDataSource.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id")
    fun getUserById(id: Int): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    fun getCurrentUser(): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: SubscriptionEntity)

    @Query("SELECT * FROM subscriptions LIMIT 1")
    fun getActiveSubscription(): Flow<SubscriptionEntity?>

    @Query("DELETE FROM users")
    suspend fun clearUser()

    @Query("DELETE FROM subscriptions")
    suspend fun clearSubscription()
}
