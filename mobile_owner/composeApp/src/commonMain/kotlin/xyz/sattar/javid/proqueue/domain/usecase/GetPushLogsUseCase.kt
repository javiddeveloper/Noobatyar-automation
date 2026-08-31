package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.BusinessRepository

class GetPushLogsUseCase(private val repository: BusinessRepository) {
    suspend operator fun invoke(
        businessId: Long,
        page: Int,
        pageSize: Int = 20,
        status: String? = null,
        search: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null
    ) = repository.getPushLogs(businessId, page, pageSize, status, search, dateFrom, dateTo)
}
