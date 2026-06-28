package xyz.sattar.javid.proqueue.data.remoteDataSource.user.mapper

import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SendOTPResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.UserDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.VerifyOTPResponseDto
import xyz.sattar.javid.proqueue.domain.model.user.SendOTP
import xyz.sattar.javid.proqueue.domain.model.user.User
import xyz.sattar.javid.proqueue.domain.model.user.VerifyOTP

fun UserDto.toDomain(): User = User(
    id = id,
    phone = phone,
    name = name,
    userType = userType,
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


