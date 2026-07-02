package xyz.sattar.javid.proqueue.feature.clientAppointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.ClientAppointmentDto
import xyz.sattar.javid.proqueue.domain.usecase.appointment.GetClientAppointmentsUseCase

data class ClientAppointmentsState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val appointments: List<ClientAppointmentDto> = emptyList()
)

class ClientAppointmentsViewModel(
    private val getClientAppointmentsUseCase: GetClientAppointmentsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ClientAppointmentsState())
    val state: StateFlow<ClientAppointmentsState> = _state.asStateFlow()

    init {
        loadAppointments()
    }

    fun loadAppointments() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val response = getClientAppointmentsUseCase()
                _state.update { 
                    it.copy(
                        isLoading = false,
                        appointments = response
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "خطا در دریافت لیست نوبت‌ها"
                    )
                }
            }
        }
    }
}
