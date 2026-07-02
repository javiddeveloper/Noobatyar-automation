package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.model.business.Business

data class ClientBusinessDetailState(
    val business: Business? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ClientBusinessDetailViewModel(
    private val repository: BusinessRepository,
    private val appointmentApi: xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.AppointmentApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClientBusinessDetailState())
    val uiState: StateFlow<ClientBusinessDetailState> = _uiState.asStateFlow()

    fun loadBusiness(id: Long) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val business = repository.getBusinessById(id)
            if (business != null) {
                _uiState.update { it.copy(business = business, isLoading = false) }
            } else {
                _uiState.update { it.copy(error = "کسب و کار یافت نشد", isLoading = false) }
            }
        }
    }

    fun createAppointment(businessId: Long, date: Long, time: String, onResult: (Boolean, String) -> Unit) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val combinedDateTime = xyz.sattar.javid.proqueue.core.utils.DateTimeUtils.combineDateAndTime(date, time)
                val response = appointmentApi.createClientAppointment(
                    businessId = businessId,
                    appointmentDate = combinedDateTime
                )
                _uiState.update { it.copy(isLoading = false) }
                if (response is xyz.sattar.javid.proqueue.core.network.ApiResponse.Success) {
                    onResult(true, "نوبت شما با موفقیت ثبت شد و در انتظار تایید است")
                } else if (response is xyz.sattar.javid.proqueue.core.network.ApiResponse.Error) {
                    onResult(false, response.message)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                onResult(false, "خطا در ارتباط با سرور")
            }
        }
    }
}
