package xyz.sattar.javid.proqueue.domain.usecase.appointment

import xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.ClientAppointmentDto
import xyz.sattar.javid.proqueue.domain.AppointmentRepository

class GetClientAppointmentsUseCase(
    private val appointmentRepository: AppointmentRepository
) {
    suspend operator fun invoke(): List<ClientAppointmentDto> {
        return appointmentRepository.getClientAppointments()
    }
}
