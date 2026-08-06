package xyz.sattar.javid.proqueue.domain

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogSummaryDto
import xyz.sattar.javid.proqueue.domain.model.business.Business

interface BusinessRepository {
    suspend fun upsertBusiness(business: Business): Boolean
    suspend fun loadAllBusiness(): List<Business>
    fun loadAllBusinessFlow(): kotlinx.coroutines.flow.Flow<List<Business>>
    suspend fun fetchAndCacheBusinesses(page: Int, pageSize: Int): Boolean
    suspend fun getBusinessById(businessId: Long): Business?
    suspend fun deleteBusiness(businessId: Long): Boolean
    suspend fun createBusiness(business: Business): Business?
    suspend fun updateBusiness(business: Business): Business?

    // --- SMS report (server-side log, no local cache) ---
    suspend fun getSmsLogs(
        businessId: Long,
        page: Int,
        pageSize: Int,
        status: String?,
        search: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null
    ): ApiResponse<SmsLogPageDto>

    suspend fun getSmsLogSummary(businessId: Long): ApiResponse<SmsLogSummaryDto>
}