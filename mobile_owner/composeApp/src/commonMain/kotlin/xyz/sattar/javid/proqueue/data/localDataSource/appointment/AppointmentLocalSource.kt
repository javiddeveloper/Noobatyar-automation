package xyz.sattar.javid.proqueue.data.localDataSource.appointment

import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails

/**
 * Plain-interface indirection over [AppointmentDao] so the repository layer
 * does not depend on Room directly. Room does not support the web target, so
 * this interface exists to let a non-Room implementation stand in later
 * without touching [xyz.sattar.javid.proqueue.data.repository.appointment.AppointmentRepositoryImpl].
 *
 * Speaks entirely in domain-model terms ([Appointment] / [AppointmentWithDetails])
 * rather than the Room-annotated `*Entity` types — those, along with Room
 * itself, live in the `roomMain` source set that `commonMain` cannot see. See
 * docs/OWNER_WEB_PLAN.md section 5.
 *
 * Declares exactly the methods that repository actually calls today; the
 * Room [AppointmentDao] has extra methods used only internally by its own
 * default-implemented [AppointmentDao.removeAppointmentAndReorder] — those
 * are intentionally not part of this surface.
 */
interface AppointmentLocalSource {
    suspend fun getWaitingQueue(businessId: Long, date: Long): List<AppointmentWithDetails>

    suspend fun getAllWaitingAppointments(businessId: Long): List<AppointmentWithDetails>

    suspend fun getTodayAppointments(businessId: Long): List<AppointmentWithDetails>

    suspend fun getAllAppointmentsForBusiness(businessId: Long): List<AppointmentWithDetails>

    suspend fun getVisitorHistory(visitorId: Long): List<AppointmentWithDetails>

    suspend fun getVisitorHistoryForBusiness(visitorId: Long, businessId: Long): List<AppointmentWithDetails>

    suspend fun upsertAppointment(appointment: Appointment): Long

    suspend fun upsertAppointments(appointments: List<Appointment>)

    suspend fun updateAppointmentStatus(appointmentId: Long, status: String, updatedAt: Long)

    suspend fun updateAppointment(appointmentId: Long, date: Long, duration: Int?, description: String?, selectedServices: String?, updatedAt: Long)

    suspend fun getTodayAppointmentsCount(businessId: Long, date: Long): Int

    suspend fun getTodayNoShowCount(businessId: Long, date: Long): Int

    suspend fun getTodayCancelledCount(businessId: Long, date: Long): Int

    suspend fun removeAppointmentAndReorder(appointmentId: Long)

    suspend fun getAppointmentById(appointmentId: Long): Appointment?

    suspend fun getConflictingAppointments(
        businessId: Long,
        startTime: Long,
        endTime: Long,
        defaultDuration: Int
    ): List<AppointmentWithDetails>

    suspend fun getAppointmentsForDate(businessId: Long, date: Long): List<AppointmentWithDetails>

    suspend fun deleteAppointmentsByVisitorId(visitorId: Long)
}
