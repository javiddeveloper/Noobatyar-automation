package xyz.sattar.javid.proqueue.data.localDataSource.user

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Int,
    val phone: String,
    val name: String,
    val userType: String,
    val isEmployee: Boolean,
    val joinedAt: String
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: Int = 1,
    val planName: String?,
    val startedAt: String?,
    val endsAt: String?,
    val isValid: Boolean
)
