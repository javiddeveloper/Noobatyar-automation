package xyz.sattar.javid.proqueue.data.repository.visitor

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.VisitorDao
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.toDomain
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.toEntity
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.toRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.VisitorApiService
import xyz.sattar.javid.proqueue.domain.VisitorPaging
import xyz.sattar.javid.proqueue.domain.VisitorRepository
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

class VisitorRepositoryImpl(
    private val visitorDao: VisitorDao,
    private val visitorApiService: VisitorApiService
) : VisitorRepository {

    override suspend fun getVisitors(page: Int, pageSize: Int, query: String?): VisitorPaging {
        var hasMore = false
        var totalCount = 0
        
        try {
            when (val response = visitorApiService.getVisitors(page, pageSize, query)) {
                is ApiResponse.Success -> {
                    val entities = response.data.results.map { it.toEntity() }
                    visitorDao.upsertVisitors(entities)
                    hasMore = response.data.next != null
                    totalCount = response.data.count
                }
                is ApiResponse.Error -> {}
            }
        } catch (e: Exception) {}

        val offset = (page - 1) * pageSize
        val items = visitorDao.getVisitors(pageSize, offset, query).map { it.toDomain() }
        
        if (totalCount == 0 && items.isNotEmpty()) {
            hasMore = items.size >= pageSize
        } else if (totalCount > 0) {
            hasMore = (offset + items.size) < totalCount
        }

        return VisitorPaging(
            visitors = items,
            totalCount = totalCount,
            hasMore = hasMore
        )
    }

    override suspend fun getVisitorById(visitorId: Long): Visitor? {
        try {
            if (visitorId > 0) {
                when (val response = visitorApiService.getVisitorById(visitorId)) {
                    is ApiResponse.Success -> {
                        val entity = response.data.toEntity()
                        visitorDao.upsertVisitor(entity)
                        return entity.toDomain()
                    }
                    is ApiResponse.Error -> {}
                }
            }
        } catch (e: Exception) {}
        return visitorDao.getVisitorById(visitorId)?.toDomain()
    }

    private suspend fun createVisitorRemote(visitor: Visitor): Visitor? {
        return try {
            when (val response = visitorApiService.createVisitor(visitor.toRequestDto())) {
                is ApiResponse.Success -> {
                    val entity = response.data.toEntity()
                    visitorDao.upsertVisitor(entity)
                    entity.toDomain()
                }
                is ApiResponse.Error -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun createVisitor(visitor: Visitor): Long {
        val remoteVisitor = createVisitorRemote(visitor)
        if (remoteVisitor != null) {
            return remoteVisitor.id
        }
        return visitorDao.upsertVisitor(visitor.toEntity())
    }

    override suspend fun updateVisitor(visitor: Visitor): Boolean {
        try {
            if (visitor.id > 0) {
                when (val response = visitorApiService.updateVisitor(visitor.id, visitor.toRequestDto())) {
                    is ApiResponse.Success -> {
                        val entity = response.data.toEntity()
                        visitorDao.upsertVisitor(entity)
                        return true
                    }
                    is ApiResponse.Error -> {
                        visitorDao.upsertVisitor(visitor.toEntity())
                        return false
                    }
                }
            }
        } catch (e: Exception) {
            visitorDao.upsertVisitor(visitor.toEntity())
        }
        return false
    }

    override suspend fun deleteVisitor(visitorId: Long): ApiResponse<Unit> {
        return try {
            if (visitorId > 0) {
                val response = visitorApiService.deleteVisitor(visitorId)
                if (response is ApiResponse.Success) {
                    visitorDao.deleteVisitor(visitorId)
                }
                response
            } else {
                visitorDao.deleteVisitor(visitorId)
                ApiResponse.Success(Unit)
            }
        } catch (e: Exception) {
            ApiResponse.Error(e.message ?: "Unknown error", 500)
        }
    }
}
