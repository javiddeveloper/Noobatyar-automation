package xyz.sattar.javid.proqueue.data.remoteDataSource.business.model

import kotlinx.serialization.Serializable

/** One day's appointment count, for the home 7-day chart. */
@Serializable
data class DailyCountDto(
    val date: String,
    val count: Int
)
