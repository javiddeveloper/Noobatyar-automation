package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.VisitorRepository
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

class VisitorUpsertUseCase(
    private val visitorRepository: VisitorRepository
) {
    suspend operator fun invoke(visitor: Visitor): Long {
        return if (visitor.id == 0L) {
            visitorRepository.createVisitor(visitor)
        } else {
            val success = visitorRepository.updateVisitor(visitor)
            if (success) visitor.id else -1L
        }
    }
}
