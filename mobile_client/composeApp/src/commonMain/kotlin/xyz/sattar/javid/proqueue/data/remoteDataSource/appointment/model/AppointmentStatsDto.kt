package xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppointmentStatsDto(
    @SerialName("total_appointments") val totalAppointments: Int = 0,
    @SerialName("completed_appointments") val completedAppointments: Int = 0,
    @SerialName("no_show_appointments") val noShowAppointments: Int = 0,
    @SerialName("total_visitors") val totalVisitors: Int = 0
)
