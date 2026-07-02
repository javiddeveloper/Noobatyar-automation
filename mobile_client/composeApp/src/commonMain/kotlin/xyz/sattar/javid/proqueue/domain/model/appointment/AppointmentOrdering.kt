package xyz.sattar.javid.proqueue.domain.model.appointment

enum class AppointmentOrdering(val value: String) {
    DATE_ASC("appointment_date"),
    DATE_DESC("-appointment_date"),
    CREATED_AT_ASC("created_at"),
    CREATED_AT_DESC("-created_at")
}
