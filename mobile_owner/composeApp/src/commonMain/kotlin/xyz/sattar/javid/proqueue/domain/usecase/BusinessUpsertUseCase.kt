package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.model.business.Business
import kotlin.time.ExperimentalTime


class BusinessUpsertUseCase(private val repository: BusinessRepository) {
    @OptIn(ExperimentalTime::class)
    suspend operator fun invoke(business: Business): Business? {
        return if (business.id == 0L) {
            repository.createBusiness(
                business.copy(
                    createdAt = DateTimeUtils.systemCurrentMilliseconds()
                )
            )
        } else {
            repository.updateBusiness(business)
        }
    }
}