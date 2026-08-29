package xyz.sattar.javid.proqueue.data.localDataSource.visitor

import kotlinx.coroutines.flow.MutableStateFlow
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

// See InMemoryBusinessLocalSource for the rationale.
class InMemoryVisitorLocalSource : VisitorLocalSource {
    private val state = MutableStateFlow<Map<Long, Visitor>>(emptyMap())
    private var nextId = 1L

    override suspend fun upsertVisitor(visitor: Visitor): Long {
        val id = if (visitor.id != 0L) visitor.id else nextId++
        state.value = state.value + (id to visitor.copy(id = id))
        return id
    }

    override suspend fun upsertVisitors(visitors: List<Visitor>) {
        visitors.forEach { upsertVisitor(it) }
    }

    override suspend fun getVisitorById(visitorId: Long): Visitor? = state.value[visitorId]

    override suspend fun getVisitors(limit: Int, offset: Int, query: String?): List<Visitor> {
        // VisitorDao.getVisitors orders by fullName ASC, not recency — sorting
        // by createdAt here would silently reorder the visitor list on web
        // (e.g. newest visitor first instead of alphabetical).
        val all = state.value.values
            .filter {
                query.isNullOrBlank() ||
                    it.fullName.contains(query, ignoreCase = true) ||
                    it.phoneNumber.contains(query)
            }
            .sortedBy { it.fullName }
        return all.drop(offset).take(limit)
    }

    override suspend fun clearAllVisitors() {
        state.value = emptyMap()
    }

    override suspend fun deleteVisitor(visitorId: Long) {
        state.value = state.value - visitorId
    }
}
