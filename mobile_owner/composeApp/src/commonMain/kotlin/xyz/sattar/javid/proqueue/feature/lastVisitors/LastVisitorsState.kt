package xyz.sattar.javid.proqueue.feature.lastVisitors

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails

@Immutable
data class LastVisitorsState(
    val isLoading: Boolean = false,
    val appointments: List<AppointmentWithDetails> = emptyList(),
    val totalCount: Int = 0,
    val message: String? = null,
    val selectedAppointmentId: Long? = null,
    val showOptionsDialog: Boolean = false,
    val selectedTab: Int = 1,
    val filter: AppointmentFilter = AppointmentFilter(),
    val showFilterSheet: Boolean = false
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class ShowMessage(val message: String) : PartialState()
        data class LoadAppointments(
            val appointments: List<AppointmentWithDetails>,
            val totalCount: Int
        ) : PartialState()
        data class ShowOptionsDialog(val appointmentId: Long?) : PartialState()
        data class TabSelected(val index: Int) : PartialState()
        data class UpdateFilter(val filter: AppointmentFilter) : PartialState()
        data class ShowFilterSheet(val show: Boolean) : PartialState()
    }
}

data class AppointmentFilter(
    val status: String? = null,
    val date: Long? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val ordering: AppointmentOrdering = AppointmentOrdering.DATE_DESC
)

