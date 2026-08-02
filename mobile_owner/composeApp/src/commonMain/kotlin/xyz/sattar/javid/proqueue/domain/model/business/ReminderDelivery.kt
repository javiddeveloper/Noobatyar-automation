package xyz.sattar.javid.proqueue.domain.model.business

/**
 * How the appointment reminder actually reaches the client
 * (backend: Business.REMINDER_DELIVERY_CHOICES).
 *
 * This is *only* about the client's reminder. The owner never receives a
 * reminder SMS from this setting — new-booking notices to the owner are a
 * separate switch, and everything else the owner sees is a push notification.
 */
enum class ReminderDelivery(val value: String) {
    /** The owner sends the reminder themselves, from their own SIM. Free. */
    MANUAL("MANUAL"),

    /** The server sends it automatically. Costs one SMS of the plan's quota. */
    PANEL("PANEL");

    companion object {
        fun fromValue(value: String?): ReminderDelivery =
            entries.firstOrNull { it.value == value } ?: MANUAL
    }
}
