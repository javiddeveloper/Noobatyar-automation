package xyz.sattar.javid.proqueue.feature.createAppointment

sealed interface CreateAppointmentIntent {
    data class LoadAppointment(val appointmentId: Long) : CreateAppointmentIntent
    data class SelectVisitor(val visitorId: Long) : CreateAppointmentIntent
    data class LoadDailyAppointments(val date: Long) : CreateAppointmentIntent
    data class UpdateDateTime(val date: Long?, val time: String?) : CreateAppointmentIntent
    data class CreateAppointment(
        val visitorId: Long,
        val appointmentDate: Long,
        val serviceDuration: Int?,
        val description: String?,
        val selectedServices: List<String> = emptyList(),
        val force: Boolean = false
    ) : CreateAppointmentIntent
    data object BackPress : CreateAppointmentIntent
    data object AppointmentCreated : CreateAppointmentIntent
    data object DismissConflictDialog : CreateAppointmentIntent
    data object DismissQuotaDialog : CreateAppointmentIntent
    data class DeleteAppointment(val appointmentId: Long) : CreateAppointmentIntent
    /** category comes from the currently selected business, resolved in the ViewModel. */
    data object LoadServiceCatalog : CreateAppointmentIntent
    data class AddServiceCatalogItem(val name: String) : CreateAppointmentIntent
    data class UpdateSelectedServices(val services: List<String>) : CreateAppointmentIntent
}
