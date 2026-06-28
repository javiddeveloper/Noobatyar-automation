package xyz.sattar.javid.proqueue.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VersionInfo(
    @SerialName("is_outdated") val isOutdated: Boolean,
    @SerialName("force_update") val forceUpdate: Boolean,
    @SerialName("latest_version") val latestVersion: LatestVersion
)

@Serializable
data class LatestVersion(
    @SerialName("version_name") val versionName: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("force_update") val forceUpdate: Boolean,
    val changelog: Changelog
)

@Serializable
data class Changelog(
    val changes: List<String>
)
