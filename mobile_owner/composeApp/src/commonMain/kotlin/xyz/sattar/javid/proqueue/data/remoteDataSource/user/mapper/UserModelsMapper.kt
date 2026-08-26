package xyz.sattar.javid.proqueue.data.remoteDataSource.user.mapper

import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SendOTPResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SubscriptionDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.UserDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.VerifyOTPResponseDto
import xyz.sattar.javid.proqueue.domain.model.user.SendOTP
import xyz.sattar.javid.proqueue.domain.model.user.Subscription
import xyz.sattar.javid.proqueue.domain.model.user.User
import xyz.sattar.javid.proqueue.domain.model.user.UserRole
import xyz.sattar.javid.proqueue.domain.model.user.VerifyOTP

fun UserDto.toDomain(): User = User(
    id = id,
    phone = phone,
    name = name,
    userType = UserRole.fromString(userType),
    isEmployee = isEmployee,
    joinedAt = joinedAt
)

fun SendOTPResponseDto.toDomain(): SendOTP = SendOTP(
    expiresIn = expiresIn
)

fun VerifyOTPResponseDto.toDomain(): VerifyOTP = VerifyOTP(
    resetToken = resetToken,
    expiresIn = expiresIn
)

// Matches the field set the old SubscriptionEntity(...) construction in
// UserRepositoryImpl.syncSubscription used to build directly from this DTO.
fun SubscriptionDto.toDomain(): Subscription = Subscription(
    id = id ?: 1,
    planName = plan?.name,
    startedAt = startedAt,
    endsAt = endsAt,
    isValid = isValid ?: false
)


