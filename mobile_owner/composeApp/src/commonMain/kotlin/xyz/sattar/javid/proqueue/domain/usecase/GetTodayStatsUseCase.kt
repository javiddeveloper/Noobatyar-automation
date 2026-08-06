package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.feature.home.DashboardStats
import kotlin.time.ExperimentalTime

class GetTodayStatsUseCase(private val repository: AppointmentRepository) {
    @OptIn(ExperimentalTime::class)
    suspend operator fun invoke(businessId: Long): DashboardStats {
        val todayAppointments = repository.getTodayAppointments(businessId)
        
        val total = todayAppointments.size
        // "COMPLETED" (service finished), not "CONFIRMED" (owner approved but not
        // yet served) — the two are distinct statuses and this card previously
        // showed confirmed-but-unserved appointments as "completed".
        val completed = todayAppointments.count { it.appointment.status == "COMPLETED" }
        val noShow = todayAppointments.count { it.appointment.status == "NO_SHOW" }
        val cancelled = todayAppointments.count { it.appointment.status == "CANCELLED" }
        // For total visitors, we can count unique visitor IDs in today's appointments or fetch from visitor repo.
        // Assuming we want unique visitors today:
        val uniqueVisitors = todayAppointments.map { it.visitor.fullName }.distinct().size

        return DashboardStats(
            totalAppointments = total,
            completedAppointments = completed,
            noShowAppointments = noShow,
            cancelledAppointments = cancelled,
            totalVisitors = uniqueVisitors
        )
    }
}
