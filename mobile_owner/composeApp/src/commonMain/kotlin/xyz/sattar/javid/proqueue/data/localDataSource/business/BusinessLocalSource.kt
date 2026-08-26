package xyz.sattar.javid.proqueue.data.localDataSource.business

import kotlinx.coroutines.flow.Flow
import xyz.sattar.javid.proqueue.domain.model.business.Business

/**
 * Plain-interface indirection over [BusinessDao] so the repository layer does
 * not depend on Room directly (Room has no web target). See
 * [xyz.sattar.javid.proqueue.data.localDataSource.appointment.AppointmentLocalSource]
 * for the full rationale. Speaks in [Business] (domain model), not the
 * Room-annotated `BusinessEntity`.
 */
interface BusinessLocalSource {
    suspend fun upsertBusiness(business: Business)

    suspend fun upsertBusinesses(businesses: List<Business>)

    suspend fun loadAllBusiness(): List<Business>

    fun loadAllBusinessFlow(): Flow<List<Business>>

    suspend fun getBusinessById(businessId: Long): Business?

    suspend fun deleteBusiness(businessId: Long)

    suspend fun clearAllBusinesses()
}
