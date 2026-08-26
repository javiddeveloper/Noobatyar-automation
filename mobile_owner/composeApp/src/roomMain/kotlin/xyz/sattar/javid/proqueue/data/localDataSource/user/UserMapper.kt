package xyz.sattar.javid.proqueue.data.localDataSource.user

import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.UserDto
import xyz.sattar.javid.proqueue.domain.model.user.Subscription
import xyz.sattar.javid.proqueue.domain.model.user.User
import xyz.sattar.javid.proqueue.domain.model.user.UserRole

fun UserDto.toEntity(): UserEntity = UserEntity(
    id = id,
    phone = phone,
    name = name,
    userType = userType,
    isEmployee = isEmployee,
    joinedAt = joinedAt
)

fun UserEntity.toDomain(): User = User(
    id = id,
    phone = phone,
    name = name,
    userType = UserRole.fromString(userType),
    isEmployee = isEmployee,
    joinedAt = joinedAt
)

// Domain -> Entity, the direction the Room adapter needs to persist what the
// repository hands it (a [User], not the Room-annotated entity).
fun User.toEntity(): UserEntity = UserEntity(
    id = id,
    phone = phone,
    name = name,
    userType = userType.value,
    isEmployee = isEmployee,
    joinedAt = joinedAt
)

fun SubscriptionEntity.toDomain(): Subscription = Subscription(
    id = id,
    planName = planName,
    startedAt = startedAt,
    endsAt = endsAt,
    isValid = isValid
)

fun Subscription.toEntity(): SubscriptionEntity = SubscriptionEntity(
    id = id,
    planName = planName,
    startedAt = startedAt,
    endsAt = endsAt,
    isValid = isValid
)
