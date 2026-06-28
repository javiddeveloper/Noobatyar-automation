package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.VisitorRepository
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

class GetVisitorByIdUseCase(
    private val visitorRepository: VisitorRepository
) {
    suspend operator fun invoke(visitorId: Long): Visitor? {
        return visitorRepository.getVisitorById(visitorId)
    }
}
