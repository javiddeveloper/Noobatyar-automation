package xyz.sattar.javid.proqueue.feature.businessList

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.usecase.ObserveBusinessesUseCase
import xyz.sattar.javid.proqueue.domain.usecase.FetchBusinessesUseCase
import xyz.sattar.javid.proqueue.feature.businessList.BusinessListEvent.*
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class BusinessListViewModel(
    private val observeBusinessesUseCase: ObserveBusinessesUseCase,
    private val fetchBusinessesUseCase: FetchBusinessesUseCase,
    private val deleteBusinessUseCase: xyz.sattar.javid.proqueue.domain.usecase.DeleteBusinessUseCase
) : BaseViewModel<BusinessListState, BusinessListState.PartialState, BusinessListEvent, BusinessListIntent>(
    initialState = BusinessListState()
) {
    override fun handleIntent(intent: BusinessListIntent): Flow<BusinessListState.PartialState> {
        return when (intent) {
            BusinessListIntent.ObserveBusinesses -> observeData()
            BusinessListIntent.LoadNextPage -> loadNextPage()
            BusinessListIntent.RetryFetch -> retryFetch()
            is BusinessListIntent.OnBusinessClick -> {
                sendEvent(NavigateToMain(intent.business))
                flow { }
            }
            BusinessListIntent.OnCreateBusinessClick -> {
                sendEvent(NavigateToCreateBusiness)
                flow { }
            }
            is BusinessListIntent.OnEditBusinessClick -> {
                sendEvent(NavigateToEditBusiness(intent.businessId))
                flow { }
            }
            is BusinessListIntent.OnDeleteBusinessClick -> deleteBusiness(intent.businessId)
        }
    }

    private fun deleteBusiness(businessId: Long): Flow<BusinessListState.PartialState> = flow {
        emit(BusinessListState.PartialState.IsLoading(true))
        try {
            val success = deleteBusinessUseCase(businessId)
            if (!success) {
                sendEvent(BusinessListEvent.ShowMessage("خطا در حذف کسب و کار"))
            }
        } catch (e: Exception) {
            sendEvent(BusinessListEvent.ShowMessage("خطا در حذف کسب و کار"))
        } finally {
            emit(BusinessListState.PartialState.IsLoading(false))
        }
    }

    override fun reduceState(
        currentState: BusinessListState,
        partialState: BusinessListState.PartialState
    ): BusinessListState {
        return when (partialState) {
            is BusinessListState.PartialState.IsLoading ->
                currentState.copy(isLoading = partialState.isLoading)
            is BusinessListState.PartialState.IsPaginating ->
                currentState.copy(isPaginating = partialState.isPaginating)
            is BusinessListState.PartialState.ShowMessage ->
                currentState.copy(message = partialState.message, isLoading = false, isPaginating = false)
            is BusinessListState.PartialState.LoadBusinesses ->
                currentState.copy(businesses = partialState.businesses, isLoading = false)
            is BusinessListState.PartialState.PageIncremented ->
                currentState.copy(currentPage = partialState.newPage)
        }
    }

    override fun createErrorState(message: String): BusinessListState.PartialState =
        BusinessListState.PartialState.ShowMessage(message)

    private fun observeData(): Flow<BusinessListState.PartialState> = flow {
        emit(BusinessListState.PartialState.IsLoading(true))
        
        viewModelScope.launch {
            try {
                val success = fetchBusinessesUseCase(page = 1, pageSize = 20)
                if (!success && uiState.value.businesses.isEmpty()) {
                    sendEvent(BusinessListEvent.ShowMessage("Failed to fetch businesses"))
                }
            } catch (e: Exception) {
                // Ignore, flow will catch and db will just show empty/cached
            }
        }
        
        observeBusinessesUseCase().collect { businesses ->
            emit(BusinessListState.PartialState.LoadBusinesses(businesses))
        }
    }

    private fun loadNextPage(): Flow<BusinessListState.PartialState> = flow {
        val currentState = uiState.value
        if (currentState.isLoading || currentState.isPaginating) return@flow
        
        emit(BusinessListState.PartialState.IsPaginating(true))
        
        val nextPage = currentState.currentPage + 1
        try {
            val success = fetchBusinessesUseCase(page = nextPage, pageSize = 20)
            if (success) {
                emit(BusinessListState.PartialState.PageIncremented(nextPage))
            } else {
                emit(BusinessListState.PartialState.ShowMessage("Failed to load more businesses"))
            }
        } catch (e: Exception) {
            emit(BusinessListState.PartialState.ShowMessage("Unknown error"))
        } finally {
            emit(BusinessListState.PartialState.IsPaginating(false))
        }
    }

    private fun retryFetch(): Flow<BusinessListState.PartialState> = flow {
        val currentState = uiState.value
        if (currentState.isLoading || currentState.isPaginating) return@flow

        if (currentState.businesses.isEmpty()) {
            emit(BusinessListState.PartialState.IsLoading(true))
            try {
                val success = fetchBusinessesUseCase(page = 1, pageSize = 20)
                if (!success) {
                    sendEvent(BusinessListEvent.ShowMessage("خطا در دریافت لیست کسب و کارها"))
                }
            } catch (e: Exception) {
                sendEvent(BusinessListEvent.ShowMessage("خطا در اتصال به سرور"))
            } finally {
                emit(BusinessListState.PartialState.IsLoading(false))
            }
        } else {
            emit(BusinessListState.PartialState.IsPaginating(true))
            val nextPage = currentState.currentPage + 1
            try {
                val success = fetchBusinessesUseCase(page = nextPage, pageSize = 20)
                if (success) {
                    emit(BusinessListState.PartialState.PageIncremented(nextPage))
                } else {
                    sendEvent(BusinessListEvent.ShowMessage("خطا در دریافت ادامه لیست"))
                }
            } catch (e: Exception) {
                sendEvent(BusinessListEvent.ShowMessage("خطا در اتصال به سرور"))
            } finally {
                emit(BusinessListState.PartialState.IsPaginating(false))
            }
        }
    }
}
