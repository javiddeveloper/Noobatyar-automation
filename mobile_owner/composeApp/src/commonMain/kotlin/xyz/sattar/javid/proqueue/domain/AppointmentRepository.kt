package xyz.sattar.javid.proqueue.domain

import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.model.DashboardStats
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering

interface AppointmentRepository {
    suspend fun createAppointment(appointment: Appointment): Long
    suspend fun getWaitingQueue(businessId: Long, date: Long): List<AppointmentWithDetails>
    suspend fun getTodayAppointments(businessId: Long): List<AppointmentWithDetails>
    suspend fun getAllAppointmentsForBusiness(businessId: Long): List<AppointmentWithDetails>
    suspend fun updateAppointmentStatus(appointmentId: Long, status: String): Boolean
    suspend fun updateAppointment(appointmentId: Long, date: Long, duration: Int?, description: String?): Boolean
    suspend fun removeAppointment(appointmentId: Long): Boolean
    suspend fun getVisitorHistory(visitorId: Long): List<AppointmentWithDetails>
    suspend fun getVisitorHistoryForBusiness(visitorId: Long, businessId: Long): List<AppointmentWithDetails>
    suspend fun getTodayStats(businessId: Long, date: Long): DashboardStats
    suspend fun getAppointmentById(appointmentId: Long): Appointment?
    suspend fun getAllWaitingAppointments(businessId: Long): List<AppointmentWithDetails>
    suspend fun deleteAppointmentsByVisitorId(visitorId: Long)
    suspend fun getConflictingAppointments(businessId: Long, startTime: Long, endTime: Long, defaultDuration: Int): List<AppointmentWithDetails>
    suspend fun getAppointmentsForDate(businessId: Long, date: Long): List<AppointmentWithDetails>

    suspend fun syncAppointments(
        businessId: Long,
        visitorId: Long? = null,
        status: String? = null,
        date: Long? = null,
        dateFrom: Long? = null,
        dateTo: Long? = null,
        ordering: AppointmentOrdering? = AppointmentOrdering.DATE_DESC,
        page: Int = 1,
        pageSize: Int = 20
    ): Boolean
}
