package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.VisitorPaging
import xyz.sattar.javid.proqueue.domain.VisitorRepository

class GetAllVisitorsUseCase(private val repository: VisitorRepository) {
    suspend operator fun invoke(page: Int, pageSize: Int, query: String? = null): VisitorPaging =
        repository.getVisitors(page, pageSize, query)
}
