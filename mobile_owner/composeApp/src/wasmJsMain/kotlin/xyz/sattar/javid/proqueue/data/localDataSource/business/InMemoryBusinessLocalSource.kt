package xyz.sattar.javid.proqueue.data.localDataSource.business

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import xyz.sattar.javid.proqueue.domain.model.business.Business

/**
 * Room has no web target (docs/OWNER_WEB_PLAN.md section 5) so wasmJs backs
 * every `*LocalSource` with a plain in-memory map instead of a real
 * database. This is acceptable specifically *because* the local database was
 * never the source of truth anywhere in this app — `AppDatabase`'s own kdoc
 * calls it a disposable cache that gets thrown away and refetched from the
 * server. The only behavior difference on web is that a page refresh clears
 * the cache too (a full reload, not just navigation), which is irrelevant
 * for a panel that's always online.
 */
class InMemoryBusinessLocalSource : BusinessLocalSource {
    private val state = MutableStateFlow<Map<Long, Business>>(emptyMap())

    override suspend fun upsertBusiness(business: Business) {
        state.value = state.value + (business.id to business)
    }

    override suspend fun upsertBusinesses(businesses: List<Business>) {
        state.value = state.value + businesses.associateBy { it.id }
    }

    // BusinessDao.loadAllBusiness(Flow) orders by createdAt DESC; without this
    // the list order would depend on Map iteration/insertion order instead,
    // which silently reshuffles the business list on web.
    override suspend fun loadAllBusiness(): List<Business> =
        state.value.values.sortedByDescending { it.createdAt }

    override fun loadAllBusinessFlow(): Flow<List<Business>> =
        state.map { it.values.sortedByDescending { business -> business.createdAt } }

    override suspend fun getBusinessById(businessId: Long): Business? = state.value[businessId]

    override suspend fun deleteBusiness(businessId: Long) {
        state.value = state.value - businessId
    }

    override suspend fun clearAllBusinesses() {
        state.value = emptyMap()
    }
}
