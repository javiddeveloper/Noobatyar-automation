package xyz.sattar.javid.proqueue.data.localDataSource.appointment

import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails

/**
 * Adapter that sits between [AppointmentDao] (Room, speaks `*Entity`) and
 * [AppointmentLocalSource] (the interface the repository actually depends
 * on, speaks domain models). See docs/OWNER_WEB_PLAN.md section 5 for why
 * this indirection exists — Room has no web target, so nothing above this
 * class may know [AppointmentEntity] exists.
 */
class RoomAppointmentLocalSource(
    private val dao: AppointmentDao
) : AppointmentLocalSource {
    override suspend fun getWaitingQueue(businessId: Long, date: Long): List<AppointmentWithDetails> =
        dao.getWaitingQueue(businessId, date).map { it.toDomain() }

    override suspend fun getAllWaitingAppointments(businessId: Long): List<AppointmentWithDetails> =
        dao.getAllWaitingAppointments(businessId).map { it.toDomain() }

    override suspend fun getTodayAppointments(businessId: Long): List<AppointmentWithDetails> =
        dao.getTodayAppointments(businessId).map { it.toDomain() }

    override suspend fun getAllAppointmentsForBusiness(businessId: Long): List<AppointmentWithDetails> =
        dao.getAllAppointmentsForBusiness(businessId).map { it.toDomain() }

    override suspend fun getVisitorHistory(visitorId: Long): List<AppointmentWithDetails> =
        dao.getVisitorHistory(visitorId).map { it.toDomain() }

    override suspend fun getVisitorHistoryForBusiness(visitorId: Long, businessId: Long): List<AppointmentWithDetails> =
        dao.getVisitorHistoryForBusiness(visitorId, businessId).map { it.toDomain() }

    override suspend fun upsertAppointment(appointment: Appointment): Long =
        dao.upsertAppointment(appointment.toEntity())

    override suspend fun upsertAppointments(appointments: List<Appointment>) =
        dao.upsertAppointments(appointments.map { it.toEntity() })

    override suspend fun updateAppointmentStatus(appointmentId: Long, status: String, updatedAt: Long) =
        dao.updateAppointmentStatus(appointmentId, status, updatedAt)

    override suspend fun updateAppointment(appointmentId: Long, date: Long, duration: Int?, description: String?, selectedServices: String?, updatedAt: Long) =
        dao.updateAppointment(appointmentId, date, duration, description, selectedServices, updatedAt)

    override suspend fun getTodayAppointmentsCount(businessId: Long, date: Long): Int =
        dao.getTodayAppointmentsCount(businessId, date)

    override suspend fun getTodayNoShowCount(businessId: Long, date: Long): Int =
        dao.getTodayNoShowCount(businessId, date)

    override suspend fun getTodayCancelledCount(businessId: Long, date: Long): Int =
        dao.getTodayCancelledCount(businessId, date)

    override suspend fun removeAppointmentAndReorder(appointmentId: Long) =
        dao.removeAppointmentAndReorder(appointmentId)

    override suspend fun getAppointmentById(appointmentId: Long): Appointment? =
        dao.getAppointmentById(appointmentId)?.toDomain()

    override suspend fun getConflictingAppointments(
        businessId: Long,
        startTime: Long,
        endTime: Long,
        defaultDuration: Int
    ): List<AppointmentWithDetails> =
        dao.getConflictingAppointments(businessId, startTime, endTime, defaultDuration).map { it.toDomain() }

    override suspend fun getAppointmentsForDate(businessId: Long, date: Long): List<AppointmentWithDetails> =
        dao.getAppointmentsForDate(businessId, date).map { it.toDomain() }

    override suspend fun deleteAppointmentsByVisitorId(visitorId: Long) =
        dao.deleteAppointmentsByVisitorId(visitorId)
}
