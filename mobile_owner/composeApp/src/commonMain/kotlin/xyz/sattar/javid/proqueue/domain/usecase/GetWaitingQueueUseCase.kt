package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.AppointmentRepository

class GetWaitingQueueUseCase(private val repository: AppointmentRepository) {
    suspend operator fun invoke(businessId: Long, date: Long) =
        repository.getWaitingQueue(businessId, date)
}