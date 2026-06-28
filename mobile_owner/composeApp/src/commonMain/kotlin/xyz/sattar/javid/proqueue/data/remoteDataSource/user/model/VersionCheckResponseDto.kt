package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VersionCheckResponseDto(
    @SerialName("is_outdated") val isOutdated: Boolean,
    @SerialName("force_update") val forceUpdate: Boolean,
    @SerialName("latest_version") val latestVersion: LatestVersionDto
)

@Serializable
data class LatestVersionDto(
    @SerialName("version_name") val versionName: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("force_update") val forceUpdate: Boolean,
    val changelog: ChangelogDto
)

@Serializable
data class ChangelogDto(
    val changes: List<String>
)
