package xyz.sattar.javid.proqueue.feature.smsReport

sealed interface SmsReportIntent {
    /** First page + summary. Also used for retry after an error. */
    data object Load : SmsReportIntent
    data object LoadMore : SmsReportIntent
    /** null = no filter; otherwise [xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogStatus]. */
    data class SetStatusFilter(val status: String?) : SmsReportIntent
    /** Search by customer name or phone number. Debounced before reload — see the viewmodel. */
    data class SetSearchQuery(val query: String) : SmsReportIntent
    /** null = no bound; a preset covers the common owner asks without needing a date picker. */
    data class SetDateRangeFilter(val range: SmsReportDateRange) : SmsReportIntent
}

/** Quick date-range presets for the sent_at filter. */
enum class SmsReportDateRange {
    ALL, TODAY, THIS_WEEK, THIS_MONTH
}
