package xyz.sattar.javid.proqueue.feature.smsReport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.usecase.GetSmsLogSummaryUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetSmsLogsUseCase

/**
 * The server-side SMS ledger for the selected business: what was sent, what
 * failed, and how much of this month's quota is left.
 */
class SmsReportViewModel(
    private val getSmsLogsUseCase: GetSmsLogsUseCase,
    private val getSmsLogSummaryUseCase: GetSmsLogSummaryUseCase
) : BaseViewModel<SmsReportState, SmsReportState.PartialState, Unit, SmsReportIntent>(
    initialState = SmsReportState()
) {
    private val pageSize = 20

    override fun handleIntent(intent: SmsReportIntent): Flow<SmsReportState.PartialState> {
        return when (intent) {
            SmsReportIntent.Load -> load(reset = true)
            SmsReportIntent.LoadMore -> load(reset = false)
            is SmsReportIntent.SetStatusFilter -> setStatusFilter(intent.status)
        }
    }

    private fun setStatusFilter(status: String?): Flow<SmsReportState.PartialState> = flow {
        if (uiState.value.statusFilter == status) return@flow
        emit(SmsReportState.PartialState.SetStatusFilter(status))
        // Emitting the filter first clears the previous list in state (so page 1
        // of the new filter can't be appended to page 1 of the old one), but
        // flatMapMerge gives no guarantee that write has landed in uiState by
        // the time loadLogs below reads it back — so the new status is passed
        // through explicitly instead of being re-read from state.
        emitAll(loadLogs(reset = true, status = status))
    }

    private fun load(reset: Boolean): Flow<SmsReportState.PartialState> = flow {
        if (reset) emitAll(loadSummary())
        emitAll(loadLogs(reset, status = uiState.value.statusFilter))
    }

    private fun loadSummary(): Flow<SmsReportState.PartialState> = flow {
        val businessId = BusinessStateHolder.selectedBusiness.value?.id ?: return@flow
        try {
            when (val response = getSmsLogSummaryUseCase(businessId)) {
                is ApiResponse.Success -> emit(SmsReportState.PartialState.LoadSummary(response.data))
                // A missing summary card is survivable; the list below is the
                // part the owner came for, so don't fail the whole screen.
                is ApiResponse.Error -> {}
            }
        } catch (e: Exception) {
        }
    }

    private fun loadLogs(reset: Boolean, status: String?): Flow<SmsReportState.PartialState> = flow {
        val current = uiState.value
        val businessId = BusinessStateHolder.selectedBusiness.value?.id
        if (businessId == null) {
            emit(SmsReportState.PartialState.ShowError("کسب‌وکار انتخاب نشده"))
            return@flow
        }
        if (!reset && (!current.canLoadMore || current.isPaginating || current.isLoading)) return@flow

        val page = if (reset) 1 else current.currentPage + 1
        if (reset) {
            emit(SmsReportState.PartialState.IsLoading(true))
        } else {
            emit(SmsReportState.PartialState.IsPaginating(true))
        }

        try {
            val response = getSmsLogsUseCase(
                businessId = businessId,
                page = page,
                pageSize = pageSize,
                status = status
            )
            when (response) {
                is ApiResponse.Success -> emit(
                    SmsReportState.PartialState.LoadPage(
                        logs = response.data.results,
                        page = page,
                        canLoadMore = response.data.next != null
                    )
                )

                is ApiResponse.Error -> emit(failure(reset, response.message))
            }
        } catch (e: Exception) {
            emit(failure(reset, e.message ?: "خطا در دریافت گزارش پیامک‌ها"))
        }
    }

    /**
     * A failed first page blocks the screen; a failed *further* page must not —
     * the rows already on screen are still valid, so it degrades to a toast.
     */
    private fun failure(reset: Boolean, message: String): SmsReportState.PartialState =
        if (reset) SmsReportState.PartialState.ShowError(message)
        else SmsReportState.PartialState.ShowMessage(message)

    override fun reduceState(
        currentState: SmsReportState,
        partialState: SmsReportState.PartialState
    ): SmsReportState {
        return when (partialState) {
            is SmsReportState.PartialState.IsLoading ->
                currentState.copy(isLoading = partialState.loading, errorMessage = null)

            is SmsReportState.PartialState.IsPaginating ->
                currentState.copy(isPaginating = partialState.paginating)

            is SmsReportState.PartialState.LoadPage -> {
                // Append, never replace: page 2 onwards extends the list. The
                // distinctBy guards against the server re-sending a row when new
                // messages shift the window between requests.
                val merged = if (partialState.page == 1) {
                    partialState.logs
                } else {
                    (currentState.logs + partialState.logs).distinctBy { it.id }
                }
                currentState.copy(
                    logs = merged,
                    currentPage = partialState.page,
                    canLoadMore = partialState.canLoadMore,
                    isLoading = false,
                    isPaginating = false,
                    errorMessage = null
                )
            }

            is SmsReportState.PartialState.LoadSummary ->
                currentState.copy(summary = partialState.summary)

            is SmsReportState.PartialState.SetStatusFilter ->
                currentState.copy(
                    statusFilter = partialState.status,
                    logs = emptyList(),
                    currentPage = 0,
                    canLoadMore = true
                )

            is SmsReportState.PartialState.ShowError ->
                currentState.copy(
                    errorMessage = partialState.message,
                    isLoading = false,
                    isPaginating = false
                )

            is SmsReportState.PartialState.ShowMessage ->
                currentState.copy(
                    message = partialState.message,
                    isLoading = false,
                    isPaginating = false
                )
        }
    }

    override fun createErrorState(message: String): SmsReportState.PartialState =
        SmsReportState.PartialState.ShowError(message)
}
