package xyz.sattar.javid.proqueue.domain

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

data class VisitorPaging(
    val visitors: List<Visitor>,
    val totalCount: Int,
    val hasMore: Boolean
)

interface VisitorRepository {
    suspend fun getVisitors(page: Int, pageSize: Int, query: String? = null): VisitorPaging
    suspend fun getVisitorById(visitorId: Long): Visitor?
    suspend fun createVisitor(visitor: Visitor): Long
    suspend fun updateVisitor(visitor: Visitor): Boolean
    suspend fun deleteVisitor(visitorId: Long): ApiResponse<Unit>
}
