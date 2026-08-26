package xyz.sattar.javid.proqueue.data.localDataSource.visitor

import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

/**
 * Adapter that sits between [VisitorDao] (Room, speaks `VisitorEntity`) and
 * [VisitorLocalSource] (the interface the repository actually depends on,
 * speaks [Visitor]). See docs/OWNER_WEB_PLAN.md section 5.
 */
class RoomVisitorLocalSource(
    private val dao: VisitorDao
) : VisitorLocalSource {
    override suspend fun upsertVisitor(visitor: Visitor): Long =
        dao.upsertVisitor(visitor.toEntity())

    override suspend fun upsertVisitors(visitors: List<Visitor>) =
        dao.upsertVisitors(visitors.map { it.toEntity() })

    override suspend fun getVisitorById(visitorId: Long): Visitor? =
        dao.getVisitorById(visitorId)?.toDomain()

    override suspend fun getVisitors(limit: Int, offset: Int, query: String?): List<Visitor> =
        dao.getVisitors(limit, offset, query).map { it.toDomain() }

    override suspend fun clearAllVisitors() =
        dao.clearAllVisitors()

    override suspend fun deleteVisitor(visitorId: Long) =
        dao.deleteVisitor(visitorId)
}
