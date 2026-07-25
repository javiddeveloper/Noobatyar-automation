package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.AppointmentRepository

class MarkAppointmentCompletedUseCase(private val repository: AppointmentRepository) {
    /**
     * Marks the appointment as served. This used to send CONFIRMED, which only
     * re-confirmed the booking — so "تکمیل" never actually completed anything
     * and appointments could not reach a terminal state from the queue.
     */
    suspend operator fun invoke(appointmentId: Long): Boolean =
        repository.updateAppointmentStatus(appointmentId, "COMPLETED")
}
