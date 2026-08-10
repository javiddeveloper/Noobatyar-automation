package xyz.sattar.javid.proqueue.feature.smsReport

import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogSummaryDto

data class SmsReportState(
    val isLoading: Boolean = false,
    /** Set only while appending a further page, so the list stays on screen. */
    val isPaginating: Boolean = false,
    val logs: List<SmsLogDto> = emptyList(),
    val summary: SmsLogSummaryDto? = null,
    val statusFilter: String? = null,
    val searchQuery: String = "",
    val dateRangeFilter: SmsReportDateRange = SmsReportDateRange.ALL,
    val currentPage: Int = 0,
    val canLoadMore: Boolean = true,
    /** Blocking error — shown instead of the list when nothing has loaded. */
    val errorMessage: String? = null,
    /** Transient error — shown as a toast over whatever is already loaded. */
    val message: String? = null
) {
    sealed interface PartialState {
        data class IsLoading(val loading: Boolean) : PartialState
        data class IsPaginating(val paginating: Boolean) : PartialState
        /**
         * A page of results. [page] is carried along because the reducer, not
         * the loader, decides whether to replace or append — replacing on every
         * page is exactly the pagination bug this project has hit before.
         */
        data class LoadPage(
            val logs: List<SmsLogDto>,
            val page: Int,
            val canLoadMore: Boolean
        ) : PartialState

        data class LoadSummary(val summary: SmsLogSummaryDto) : PartialState
        data class SetStatusFilter(val status: String?) : PartialState
        /** Updates the query text immediately; the reload itself is debounced by the viewmodel. */
        data class SetSearchQuery(val query: String) : PartialState
        data class SetDateRangeFilter(val range: SmsReportDateRange) : PartialState
        data class ShowError(val message: String) : PartialState
        data class ShowMessage(val message: String?) : PartialState
    }
}
