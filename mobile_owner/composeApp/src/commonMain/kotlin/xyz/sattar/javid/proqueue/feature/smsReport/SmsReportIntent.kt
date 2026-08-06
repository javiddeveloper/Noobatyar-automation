package xyz.sattar.javid.proqueue.feature.smsReport

sealed interface SmsReportIntent {
    /** First page + summary. Also used for retry after an error. */
    data object Load : SmsReportIntent
    data object LoadMore : SmsReportIntent
    /** null = no filter; otherwise [xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogStatus]. */
    data class SetStatusFilter(val status: String?) : SmsReportIntent
}
