package xyz.sattar.javid.proqueue.feature.smsReport

sealed interface SmsReportIntent {
    /** First page + summary for the *currently selected* channel. Also used for retry after an error. */
    data object Load : SmsReportIntent
    data object LoadMore : SmsReportIntent
    /** null = no filter; otherwise [xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogStatus]. */
    data class SetStatusFilter(val status: String?) : SmsReportIntent
    /** Search by customer name or phone number. Debounced before reload — see the viewmodel. */
    data class SetSearchQuery(val query: String) : SmsReportIntent
    /** null = no bound; a preset covers the common owner asks without needing a date picker. */
    data class SetDateRangeFilter(val range: SmsReportDateRange) : SmsReportIntent
    /** Switches between the SMS and push ledgers; triggers a first load if that channel is still empty. */
    data class SelectChannel(val channel: ReportChannel) : SmsReportIntent
}

/** Quick date-range presets for the sent_at filter. */
enum class SmsReportDateRange {
    ALL, TODAY, THIS_WEEK, THIS_MONTH
}

/** Which delivery ledger the report is currently showing — same filters apply to both. */
enum class ReportChannel {
    SMS, PUSH
}
