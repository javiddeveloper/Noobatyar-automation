package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.domain.MessageRepository
import xyz.sattar.javid.proqueue.domain.VisitorRepository

class DeleteVisitorUseCase(
    private val visitorRepository: VisitorRepository,
    private val appointmentRepository: AppointmentRepository,
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(visitorId: Long): ApiResponse<Unit> {
        return try {
            val response = visitorRepository.deleteVisitor(visitorId)
            if (response is ApiResponse.Success) {
                messageRepository.deleteMessagesByVisitorId(visitorId)
                appointmentRepository.deleteAppointmentsByVisitorId(visitorId)
            }
            response
        } catch (e: Exception) {
            ApiResponse.Error(e.message ?: "Error deleting visitor", 500)
        }
    }
}
