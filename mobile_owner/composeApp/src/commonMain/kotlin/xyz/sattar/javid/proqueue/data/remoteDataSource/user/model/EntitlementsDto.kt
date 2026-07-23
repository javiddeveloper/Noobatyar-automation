package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Response of GET accounting/my-entitlements/ — the effective capabilities and
 * quotas of the user's current plan, plus this month's usage. Mirrors the
 * backend entitlement keys in accounting/entitlements.py.
 */
@Serializable
data class EntitlementsResponseDto(
    val entitlements: Map<String, JsonElement> = emptyMap(),
    val usage: UsageDto = UsageDto()
) {
    /** Read a boolean capability flag (e.g. "online_gateway"). */
    fun hasFeature(key: String): Boolean =
        (entitlements[key] as? JsonPrimitive)?.booleanOrNull ?: false

    /** Read a numeric quota (e.g. "monthly_appointments"). -1 means unlimited. */
    fun quota(key: String): Int =
        (entitlements[key] as? JsonPrimitive)?.intOrNull ?: 0
}

@Serializable
data class UsageDto(
    val appointments: AppointmentUsageDto = AppointmentUsageDto(),
    val sms: SmsUsageDto = SmsUsageDto()
)

@Serializable
data class AppointmentUsageDto(
    val used: Int = 0,
    val quota: Int = 0, // -1 = unlimited
    @SerialName("monthly_remaining") val monthlyRemaining: Int = 0,
    val wallet: Int = 0 // appointment credit bought via add-on packs
)

@Serializable
data class SmsUsageDto(
    val quota: Int = 0,
    @SerialName("monthly_remaining") val monthlyRemaining: Int = 0,
    val wallet: Int = 0
)

/** Canonical entitlement keys — keep in sync with backend accounting/entitlements.py. */
object EntitlementKeys {
    const val ONLINE_GATEWAY = "online_gateway"
    const val DEPOSIT = "deposit"
    const val PROMOTIONAL_SMS = "promotional_sms"
    const val CAPACITY_CONTROL = "capacity_control"
    const val ADVANCED_REPORTS = "advanced_reports"
    const val MULTI_CHANNEL = "multi_channel"
    const val BRANDED_PAGE = "branded_page"
    const val PRIORITY_SUPPORT = "priority_support"

    const val MAX_BUSINESSES = "max_businesses"
    const val MONTHLY_APPOINTMENTS = "monthly_appointments"
    const val MONTHLY_SMS = "monthly_sms"

    const val UNLIMITED = -1
}
