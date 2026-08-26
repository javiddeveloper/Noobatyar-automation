package xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.model

import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

/**
 * DTO -> domain, straight across (no Room `VisitorEntity` in between — see
 * docs/OWNER_WEB_PLAN.md section 5). Matches the previously existing
 * `VisitorDto.toEntity()` + `VisitorEntity.toDomain()` pair (now in
 * roomMain/.../visitor/VisitorMapper.kt) field-for-field.
 */
fun VisitorDto.toDomain(): Visitor = Visitor(
    id = id,
    fullName = fullName,
    phoneNumber = phoneNumber,
    createdAt = DateTimeUtils.parseIsoToEpochMillis(createdAt ?: "")
)

// Domain -> outbound request DTO. Moved here (out of the old Room-adjacent
// VisitorMapper.kt) because VisitorRepositoryImpl (commonMain) calls it
// directly and roomMain is invisible from commonMain.
fun Visitor.toRequestDto() = CreateVisitorRequestDto(
    fullName = fullName,
    phoneNumber = phoneNumber
)
