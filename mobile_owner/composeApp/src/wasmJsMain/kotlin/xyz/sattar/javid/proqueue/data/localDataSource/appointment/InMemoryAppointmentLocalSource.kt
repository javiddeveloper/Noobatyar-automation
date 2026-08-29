package xyz.sattar.javid.proqueue.data.localDataSource.appointment

import kotlinx.coroutines.flow.MutableStateFlow
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.data.localDataSource.business.BusinessLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.VisitorLocalSource
import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

// The set of statuses AppointmentDao's getWaitingQueue/getAllWaitingAppointments
// treat as "still in the queue" (see AppointmentDao.kt's `status IN (...)`
// clauses). Kept in sync by hand since this source has no SQL to read it from.
private val ACTIVE_QUEUE_STATUSES = setOf("WAITING", "PENDING_APPROVAL", "PENDING_VERIFICATION")

/**
 * See InMemoryBusinessLocalSource for the rationale. This is the one
 * `*LocalSource` whose Room original joins across tables
 * (`AppointmentWithDetails` = appointment + visitor + business), so this
 * needs read access to the other two in-memory sources to reproduce that —
 * KoinModule.wasmJs.kt wires the same [BusinessLocalSource]/[VisitorLocalSource]
 * singletons in here that the rest of the app uses, so an upsert through one
 * is visible through the other.
 */
class InMemoryAppointmentLocalSource(
    private val businessLocalSource: BusinessLocalSource,
    private val visitorLocalSource: VisitorLocalSource
) : AppointmentLocalSource {
    private val state = MutableStateFlow<Map<Long, Appointment>>(emptyMap())
    private var nextId = 1L

    private fun dayRange(date: Long): LongRange {
        val dayMillis = 24L * 60 * 60 * 1000
        val startOfDay = (date / dayMillis) * dayMillis
        return startOfDay until (startOfDay + dayMillis)
    }

    private suspend fun withDetails(appointment: Appointment): AppointmentWithDetails? {
        val visitor = visitorLocalSource.getVisitorById(appointment.visitorId) ?: Visitor(
            id = appointment.visitorId,
            fullName = "",
            phoneNumber = "",
            createdAt = 0
        )
        val business = businessLocalSource.getBusinessById(appointment.businessId) ?: return null
        return AppointmentWithDetails(appointment, visitor, business)
    }

    private suspend fun allWithDetails(businessId: Long): List<AppointmentWithDetails> =
        state.value.values
            .filter { it.businessId == businessId }
            .mapNotNull { withDetails(it) }

    override suspend fun getWaitingQueue(businessId: Long, date: Long): List<AppointmentWithDetails> {
        // AppointmentDao.getWaitingQueue compares dates via DATE(...,'localtime'),
        // i.e. the device's local calendar day, not a UTC day bucket — using
        // dayRange() (UTC-based) here would drop/include appointments near
        // midnight for any owner not in UTC. It also orders by proximity to
        // :date (ABS(appointmentDate - :date)), not chronologically, and
        // includes PENDING_APPROVAL/PENDING_VERIFICATION alongside WAITING —
        // omitting those two would silently hide pending-approval visitors
        // from the queue screen.
        return allWithDetails(businessId)
            .filter {
                DateTimeUtils.isSameDay(it.appointment.appointmentDate, date) &&
                    it.appointment.status in ACTIVE_QUEUE_STATUSES
            }
            .sortedBy { kotlin.math.abs(it.appointment.appointmentDate - date) }
    }

    override suspend fun getAllWaitingAppointments(businessId: Long): List<AppointmentWithDetails> =
        // Matches AppointmentDao.getAllWaitingAppointments' status set; see
        // getWaitingQueue above for why WAITING alone is not enough.
        allWithDetails(businessId)
            .filter { it.appointment.status in ACTIVE_QUEUE_STATUSES }
            .sortedBy { it.appointment.appointmentDate }

    override suspend fun getTodayAppointments(businessId: Long): List<AppointmentWithDetails> =
        // Despite the name, AppointmentDao.getTodayAppointments has no date
        // filter at all — it's every appointment for the business, ascending.
        // Routing this through getAppointmentsForDate(businessId, now) would
        // silently hide every appointment not on today's date, which the Room
        // version never does.
        allWithDetails(businessId).sortedBy { it.appointment.appointmentDate }

    override suspend fun getAllAppointmentsForBusiness(businessId: Long): List<AppointmentWithDetails> =
        // AppointmentDao.getAllAppointmentsForBusiness caps at LIMIT 100.
        allWithDetails(businessId).sortedByDescending { it.appointment.appointmentDate }.take(100)

    override suspend fun getVisitorHistory(visitorId: Long): List<AppointmentWithDetails> =
        state.value.values
            .filter { it.visitorId == visitorId }
            .mapNotNull { withDetails(it) }
            .sortedByDescending { it.appointment.appointmentDate }

    override suspend fun getVisitorHistoryForBusiness(visitorId: Long, businessId: Long): List<AppointmentWithDetails> =
        getVisitorHistory(visitorId).filter { it.appointment.businessId == businessId }

    override suspend fun upsertAppointment(appointment: Appointment): Long {
        val id = if (appointment.id != 0L) appointment.id else nextId++
        state.value = state.value + (id to appointment.copy(id = id))
        return id
    }

    override suspend fun upsertAppointments(appointments: List<Appointment>) {
        appointments.forEach { upsertAppointment(it) }
    }

    override suspend fun updateAppointmentStatus(appointmentId: Long, status: String, updatedAt: Long) {
        val current = state.value[appointmentId] ?: return
        state.value = state.value + (appointmentId to current.copy(status = status, updatedAt = updatedAt))
    }

    override suspend fun updateAppointment(
        appointmentId: Long,
        date: Long,
        duration: Int?,
        description: String?,
        selectedServices: String?,
        updatedAt: Long
    ) {
        val current = state.value[appointmentId] ?: return
        state.value = state.value + (appointmentId to current.copy(
            appointmentDate = date,
            serviceDuration = duration,
            description = description,
            selectedServices = selectedServices,
            updatedAt = updatedAt
        ))
    }

    override suspend fun getTodayAppointmentsCount(businessId: Long, date: Long): Int {
        val range = dayRange(date)
        return state.value.values.count { it.businessId == businessId && it.appointmentDate in range }
    }

    override suspend fun getTodayNoShowCount(businessId: Long, date: Long): Int {
        val range = dayRange(date)
        return state.value.values.count {
            it.businessId == businessId && it.appointmentDate in range && it.status == "NO_SHOW"
        }
    }

    override suspend fun getTodayCancelledCount(businessId: Long, date: Long): Int {
        val range = dayRange(date)
        return state.value.values.count {
            it.businessId == businessId && it.appointmentDate in range && it.status == "CANCELLED"
        }
    }

    override suspend fun removeAppointmentAndReorder(appointmentId: Long) {
        state.value = state.value - appointmentId
    }

    override suspend fun getAppointmentById(appointmentId: Long): Appointment? = state.value[appointmentId]

    override suspend fun getConflictingAppointments(
        businessId: Long,
        startTime: Long,
        endTime: Long,
        defaultDuration: Int
    ): List<AppointmentWithDetails> =
        allWithDetails(businessId).filter { details ->
            val appt = details.appointment
            val apptStart = appt.appointmentDate
            val apptEnd = apptStart + (appt.serviceDuration ?: defaultDuration) * 60_000L
            appt.status != "CANCELLED" && apptStart < endTime && startTime < apptEnd
        }

    override suspend fun getAppointmentsForDate(businessId: Long, date: Long): List<AppointmentWithDetails> {
        // AppointmentDao.getAppointmentsForDate also buckets by local calendar
        // day (DATE(...,'localtime')) and excludes CANCELLED — see the
        // getWaitingQueue comment above for why a UTC dayRange() would be wrong.
        return allWithDetails(businessId)
            .filter {
                DateTimeUtils.isSameDay(it.appointment.appointmentDate, date) &&
                    it.appointment.status != "CANCELLED"
            }
            .sortedBy { it.appointment.appointmentDate }
    }

    override suspend fun deleteAppointmentsByVisitorId(visitorId: Long) {
        state.value = state.value.filterValues { it.visitorId != visitorId }
    }
}
