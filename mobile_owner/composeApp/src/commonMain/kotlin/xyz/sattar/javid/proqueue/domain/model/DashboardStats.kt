package xyz.sattar.javid.proqueue.domain.model

data class DashboardStats(
    val totalAppointments: Int,
    val noShowCount: Int,
    val cancelledCount: Int
)