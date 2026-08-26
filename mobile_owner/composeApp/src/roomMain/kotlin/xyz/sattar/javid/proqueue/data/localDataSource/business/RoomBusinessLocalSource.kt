package xyz.sattar.javid.proqueue.data.localDataSource.business

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.sattar.javid.proqueue.domain.model.business.Business

/**
 * Adapter that sits between [BusinessDao] (Room, speaks `BusinessEntity`) and
 * [BusinessLocalSource] (the interface the repository actually depends on,
 * speaks [Business]). See docs/OWNER_WEB_PLAN.md section 5.
 */
class RoomBusinessLocalSource(
    private val dao: BusinessDao
) : BusinessLocalSource {
    override suspend fun upsertBusiness(business: Business) =
        dao.upsertBusiness(business.toEntity())

    override suspend fun upsertBusinesses(businesses: List<Business>) =
        dao.upsertBusinesses(businesses.map { it.toEntity() })

    override suspend fun loadAllBusiness(): List<Business> =
        dao.loadAllBusiness().map { it.toDomain() }

    override fun loadAllBusinessFlow(): Flow<List<Business>> =
        dao.loadAllBusinessFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun getBusinessById(businessId: Long): Business? =
        dao.getBusinessById(businessId)?.toDomain()

    override suspend fun deleteBusiness(businessId: Long) =
        dao.deleteBusiness(businessId)

    override suspend fun clearAllBusinesses() =
        dao.clearAllBusinesses()
}
