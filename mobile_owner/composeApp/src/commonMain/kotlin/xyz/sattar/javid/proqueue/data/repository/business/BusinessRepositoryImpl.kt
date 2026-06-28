package xyz.sattar.javid.proqueue.data.repository.business

import xyz.sattar.javid.proqueue.data.localDataSource.business.BusinessDao
import xyz.sattar.javid.proqueue.data.localDataSource.business.toDomain
import xyz.sattar.javid.proqueue.data.localDataSource.business.toEntity
import xyz.sattar.javid.proqueue.data.localDataSource.business.toRequestDto
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.model.business.Business

import xyz.sattar.javid.proqueue.data.remoteDataSource.business.BusinessApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.sattar.javid.proqueue.core.network.ApiResponse

class BusinessRepositoryImpl(
    private val businessDao: BusinessDao,
    private val businessApiService: BusinessApiService
) : BusinessRepository {
    override suspend fun upsertBusiness(business: Business): Boolean {
        return try {
            businessDao.upsertBusiness(business.toEntity())
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadAllBusiness(): List<Business> {
        return try {
            businessDao.loadAllBusiness().map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun loadAllBusinessFlow(): Flow<List<Business>> {
        return businessDao.loadAllBusinessFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun fetchAndCacheBusinesses(page: Int, pageSize: Int): Boolean {
        return try {
            when (val response = businessApiService.getBusinesses(page, pageSize)) {
                is ApiResponse.Success -> {
                    if (page == 1) {
                        businessDao.clearAllBusinesses()
                    }
                    val entities = response.data.results.map { it.toEntity() }
                    businessDao.upsertBusinesses(entities)
                    true
                }
                is ApiResponse.Error -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getBusinessById(businessId: Long): Business? {
        return try {
            businessDao.getBusinessById(businessId)?.toDomain()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deleteBusiness(businessId: Long): Boolean {
        return try {
            when (businessApiService.deleteBusiness(businessId)) {
                is ApiResponse.Success -> {
                    businessDao.deleteBusiness(businessId)
                    true
                }
                is ApiResponse.Error -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun createBusiness(business: Business): Business? {
        return try {
            when (val response = businessApiService.createBusiness(business.toRequestDto())) {
                is ApiResponse.Success -> {
                    val entity = response.data.toEntity()
                    businessDao.upsertBusiness(entity)
                    entity.toDomain()
                }
                is ApiResponse.Error -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun updateBusiness(business: Business): Business? {
        return try {
            when (val response = businessApiService.updateBusiness(business.id, business.toRequestDto())) {
                is ApiResponse.Success -> {
                    val entity = response.data.toEntity()
                    businessDao.upsertBusiness(entity)
                    entity.toDomain()
                }
                is ApiResponse.Error -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}