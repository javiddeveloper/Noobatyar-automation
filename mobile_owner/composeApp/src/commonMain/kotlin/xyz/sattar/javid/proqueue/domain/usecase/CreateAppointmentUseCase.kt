package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.core.notifications.NotificationScheduler
import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.VisitorRepository
import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CreateAppointmentUseCase(
    private val repository: AppointmentRepository,
    private val visitorRepository: VisitorRepository,
    private val businessRepository: BusinessRepository,
    private val notificationScheduler: NotificationScheduler
) {
    @OptIn(ExperimentalTime::class)
    suspend operator fun invoke(
        businessId: Long,
        visitorId: Long,
        appointmentDate: Long,
        serviceDuration: Int?,
        description: String?
    ): Long {
        val now = Clock.System.now().toEpochMilliseconds()
        val appointment = Appointment(
            id = 0,
            businessId = businessId,
            visitorId = visitorId,
            appointmentDate = appointmentDate,
            serviceDuration = serviceDuration,
            status = "WAITING",
            description = description,
            createdAt = now,
            updatedAt = now
        )
        val newId = repository.createAppointment(appointment)
        
        if (newId > 0) {
            scheduleNotification(newId, businessId, visitorId, appointmentDate)
        }
        
        return newId
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun scheduleNotification(
        appointmentId: Long,
        businessId: Long,
        visitorId: Long,
        appointmentDate: Long
    ) {
        try {
            val business = businessRepository.getBusinessById(businessId)

            if (business != null && business.notificationEnabled) {
                val minutes = business.notificationMinutesBefore
                val triggerTime = appointmentDate - (minutes * 60 * 1000)

                if (triggerTime > Clock.System.now().toEpochMilliseconds()) {
                    val visitor = visitorRepository.getVisitorById(visitorId)

                    if (visitor != null) {
                        notificationScheduler.scheduleReminder(
                            appointmentId = appointmentId,
                            customerName = visitor.fullName,
                            businessName = business.title,
                            triggerAtMillis = triggerTime,
                            minutesBefore = minutes,
                            businessId = businessId,
                            visitorId = visitorId
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
