package xyz.sattar.javid.proqueue.data.localDataSource.user

import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.UserDto
import xyz.sattar.javid.proqueue.domain.model.user.User

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
    userType = userType,
    isEmployee = isEmployee,
    joinedAt = joinedAt
)
