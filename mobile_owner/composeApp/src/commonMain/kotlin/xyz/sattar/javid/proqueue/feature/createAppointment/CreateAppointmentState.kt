package xyz.sattar.javid.proqueue.feature.createAppointment

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

@Immutable
data class CreateAppointmentState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val visitor: Visitor? = null,
    val selectedVisitorId: Long? = null,
    val appointmentDate: Long = 0L,
    val selectedDate: Long? = null,
    val selectedTime: String? = null,
    val appointmentCreated: Boolean = false,
    val serviceDuration: Int? = null,
    val description: String? = null,
    /** Service-catalog chip names picked for this appointment (category-scoped). */
    val selectedServices: List<String> = emptyList(),
    /** Existing chips for the business's category, loaded for the picker sheet. */
    val serviceCatalog: List<String> = emptyList(),
    val isServiceCatalogLoading: Boolean = false,
    val editingAppointmentId: Long? = null,
    val showConflictDialog: Boolean = false,
    val conflictingVisitorName: String? = null,
    val quotaDialogMessage: String? = null,
    val dailyAppointments: List<AppointmentWithDetails> = emptyList(),
    val dailyAppointmentsCount: Int = 0,
    val appointmentDeleted: Boolean = false
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class ShowMessage(val message: String) : PartialState()
        data class LoadVisitor(val visitor: Visitor) : PartialState()
        data class UpdateDateTime(val date: Long?, val time: String?) : PartialState()
        data class LoadAppointmentDetails(
            val visitorId: Long,
            val appointmentDate: Long,
            val serviceDuration: Int?,
            val description: String?,
            val selectedServices: List<String>,
            val appointmentId: Long
        ) : PartialState()
        data class LoadServiceCatalog(val items: List<String>) : PartialState()
        data class ServiceCatalogLoading(val isLoading: Boolean) : PartialState()
        data class UpdateSelectedServices(val services: List<String>) : PartialState()
        data object AppointmentCreated : PartialState()
        data class ShowConflictDialog(val visitorName: String) : PartialState()
        data object DismissConflictDialog : PartialState()
        data class ShowQuotaDialog(val message: String) : PartialState()
        data object DismissQuotaDialog : PartialState()
        data class LoadDailyAppointments(val appointments: List<AppointmentWithDetails>) : PartialState()
        data object AppointmentDeleted : PartialState()
    }
}
