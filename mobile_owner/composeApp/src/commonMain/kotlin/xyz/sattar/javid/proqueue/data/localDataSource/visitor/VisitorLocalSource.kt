package xyz.sattar.javid.proqueue.data.localDataSource.visitor

import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

/**
 * Plain-interface indirection over [VisitorDao] so the repository layer does
 * not depend on Room directly (Room has no web target). See
 * [xyz.sattar.javid.proqueue.data.localDataSource.appointment.AppointmentLocalSource]
 * for the full rationale. Speaks in [Visitor] (domain model), not the
 * Room-annotated `VisitorEntity`.
 */
interface VisitorLocalSource {
    suspend fun upsertVisitor(visitor: Visitor): Long

    suspend fun upsertVisitors(visitors: List<Visitor>)

    suspend fun getVisitorById(visitorId: Long): Visitor?

    suspend fun getVisitors(limit: Int, offset: Int, query: String? = null): List<Visitor>

    suspend fun clearAllVisitors()

    suspend fun deleteVisitor(visitorId: Long)
}
