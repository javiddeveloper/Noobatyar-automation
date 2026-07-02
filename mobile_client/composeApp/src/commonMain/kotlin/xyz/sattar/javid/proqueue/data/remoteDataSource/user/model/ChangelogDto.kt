package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChangelogDto(
    @SerialName("version_name") val versionName: String,
    @SerialName("version_code") val versionCode: Int,
    val changes: List<String>
)
