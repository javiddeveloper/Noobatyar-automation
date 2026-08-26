package xyz.sattar.javid.proqueue.data.localDataSource.visitor

import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.model.VisitorDto
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

fun VisitorEntity.toDomain() = Visitor(
    id = id,
    fullName = fullName,
    phoneNumber = phoneNumber,
    createdAt = createdAt,
)

fun Visitor.toEntity() = VisitorEntity(
    id = id,
    fullName = fullName,
    phoneNumber = phoneNumber,
    createdAt = createdAt,
)

// Kept for reference/symmetry with the other *Dao mappers; no longer called
// now that VisitorRepositoryImpl goes DTO -> domain directly (see
// data/remoteDataSource/visitor/model/VisitorModelsMapper.kt, commonMain).
fun VisitorDto.toEntity() = VisitorEntity(
    id = id,
    fullName = fullName,
    phoneNumber = phoneNumber,
    createdAt = DateTimeUtils.parseIsoToEpochMillis(createdAt ?: "")
)

