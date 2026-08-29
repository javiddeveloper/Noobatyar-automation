package xyz.sattar.javid.proqueue.feature.smsReport

import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PushLogDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PushLogSummaryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogSummaryDto

data class SmsReportState(
    val channel: ReportChannel = ReportChannel.SMS,
    val isLoading: Boolean = false,
    /** Set only while appending a further page, so the list stays on screen. */
    val isPaginating: Boolean = false,
    val logs: List<SmsLogDto> = emptyList(),
    val summary: SmsLogSummaryDto? = null,
    val currentPage: Int = 0,
    val canLoadMore: Boolean = true,
    // Push ledger — kept in separate fields (not reusing the SMS ones above)
    // so switching tabs doesn't lose whichever list was already loaded, and a
    // failed/loading push page can never be confused for an SMS one.
    val pushLogs: List<PushLogDto> = emptyList(),
    val pushSummary: PushLogSummaryDto? = null,
    val pushCurrentPage: Int = 0,
    val pushCanLoadMore: Boolean = true,
    /** Filters are shared across both channels — the same status vocabulary
     * (minus SKIPPED_QUOTA, which push has no equivalent of) and the same
     * search/date semantics apply to both endpoints. */
    val statusFilter: String? = null,
    val searchQuery: String = "",
    val dateRangeFilter: SmsReportDateRange = SmsReportDateRange.ALL,
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

        data class LoadPushPage(
            val logs: List<PushLogDto>,
            val page: Int,
            val canLoadMore: Boolean
        ) : PartialState

        data class LoadPushSummary(val summary: PushLogSummaryDto) : PartialState

        data class SetChannel(val channel: ReportChannel) : PartialState
        data class SetStatusFilter(val status: String?) : PartialState
        /** Updates the query text immediately; the reload itself is debounced by the viewmodel. */
        data class SetSearchQuery(val query: String) : PartialState
        data class SetDateRangeFilter(val range: SmsReportDateRange) : PartialState
        data class ShowError(val message: String) : PartialState
        data class ShowMessage(val message: String?) : PartialState
    }
}
