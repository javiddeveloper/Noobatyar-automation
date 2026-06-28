package xyz.sattar.javid.proqueue.feature.visitorSelection

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.usecase.DeleteVisitorUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetAllVisitorsUseCase

class VisitorSelectionViewModel(
    private val getAllVisitorsUseCase: GetAllVisitorsUseCase,
    private val deleteVisitorUseCase: DeleteVisitorUseCase
) : BaseViewModel<VisitorSelectionState, VisitorSelectionState.PartialState, VisitorSelectionEvent, VisitorSelectionIntent>(
    initialState = VisitorSelectionState()
) {
    private val PAGE_SIZE = 50
    private var searchJob: Job? = null

    override fun handleIntent(intent: VisitorSelectionIntent): Flow<VisitorSelectionState.PartialState> {
        return when (intent) {
            VisitorSelectionIntent.LoadVisitors -> loadVisitors(reset = true)
            VisitorSelectionIntent.LoadMore -> loadVisitors(reset = false)
            is VisitorSelectionIntent.SearchVisitors -> {
                searchVisitors()
                flow { emit(VisitorSelectionState.PartialState.UpdateSearchQuery(intent.query)) }
            }
            is VisitorSelectionIntent.SelectVisitor -> {
                sendEvent(VisitorSelectionEvent.NavigateToCreateAppointment(intent.visitorId))
            }
            VisitorSelectionIntent.CreateNewVisitor -> {
                sendEvent(VisitorSelectionEvent.NavigateToCreateVisitor)
            }
            is VisitorSelectionIntent.DeleteVisitor -> deleteVisitor(intent.visitorId)
            is VisitorSelectionIntent.EditVisitor -> {
                sendEvent(VisitorSelectionEvent.NavigateToEditVisitor(intent.visitorId))
            }
            VisitorSelectionIntent.BackPress -> {
                sendEvent(VisitorSelectionEvent.NavigateBack)
            }
        }
    }

    override fun reduceState(
        currentState: VisitorSelectionState,
        partialState: VisitorSelectionState.PartialState
    ): VisitorSelectionState {
        return when (partialState) {
            is VisitorSelectionState.PartialState.IsLoading ->
                currentState.copy(isLoading = partialState.isLoading)
            is VisitorSelectionState.PartialState.LoadVisitors -> {
                val updatedVisitors = if (partialState.page == 1) {
                    partialState.visitors
                } else {
                    (currentState.visitors + partialState.visitors).distinctBy { it.id }
                }
                currentState.copy(
                    visitors = updatedVisitors,
                    filteredVisitors = updatedVisitors,
                    isLoading = false,
                    currentPage = partialState.page,
                    canLoadMore = partialState.canLoadMore
                )
            }
            is VisitorSelectionState.PartialState.UpdateSearchQuery ->
                currentState.copy(searchQuery = partialState.query)
            is VisitorSelectionState.PartialState.ShowMessage ->
                currentState.copy(message = partialState.message, isLoading = false)
        }
    }

    override fun createErrorState(message: String): VisitorSelectionState.PartialState =
        VisitorSelectionState.PartialState.ShowMessage(message)

    private fun loadVisitors(reset: Boolean): Flow<VisitorSelectionState.PartialState> = flow {
        val currentState = uiState.value
        if (!reset && (!currentState.canLoadMore || currentState.isLoading)) return@flow

        emit(VisitorSelectionState.PartialState.IsLoading(true))
        try {
            val pageToLoad = if (reset) 1 else currentState.currentPage + 1
            val query = currentState.searchQuery.takeIf { it.isNotBlank() }
            
            val result = getAllVisitorsUseCase(page = pageToLoad, pageSize = PAGE_SIZE, query = query)

            emit(VisitorSelectionState.PartialState.LoadVisitors(result.visitors, pageToLoad, result.hasMore))
            
        } catch (e: Exception) {
            emit(VisitorSelectionState.PartialState.ShowMessage(e.message ?: "خطا در بارگذاری لیست مراجعین"))
        }
    }

    private fun searchVisitors() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            sendIntent(VisitorSelectionIntent.LoadVisitors)
        }
    }

    private fun deleteVisitor(visitorId: Long): Flow<VisitorSelectionState.PartialState> = flow {
        emit(VisitorSelectionState.PartialState.IsLoading(true))
        try {
            when (val response = deleteVisitorUseCase(visitorId)) {
                is ApiResponse.Success -> {
                    sendIntent(VisitorSelectionIntent.LoadVisitors)
                }
                is ApiResponse.Error -> {
                    emit(VisitorSelectionState.PartialState.ShowMessage(response.message))
                }
            }
        } catch (e: Exception) {
            emit(VisitorSelectionState.PartialState.ShowMessage(e.message ?: "خطا در حذف مراجع"))
        }
    }
}
