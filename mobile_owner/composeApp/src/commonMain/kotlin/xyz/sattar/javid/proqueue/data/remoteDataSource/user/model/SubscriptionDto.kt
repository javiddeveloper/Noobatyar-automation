package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SubscriptionDto(
    val id: Int? = null,
    val status: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("is_valid") val isValid: Boolean? = null,
    @SerialName("is_vip") val isVip: Boolean? = null,
    val plan: PlanDto? = null
)

@Serializable
data class PlanDto(
    val id: Int,
    val name: String,
    val price: Long,
    @SerialName("discount_price") val discountPrice: Long? = null,
    @SerialName("price_display") val priceDisplay: String,
    @SerialName("duration_display") val durationDisplay: String,
    val description: List<String> = emptyList(),
    @SerialName("is_vip") val isVip: Boolean,
    // Capabilities/quotas unlocked by this plan (see accounting/entitlements.py).
    val features: Map<String, JsonElement> = emptyMap()
)

/** آیا این پلن، پلن آزمایشی است؟ همان الگویی که در SubscriptionCard استفاده می‌شود. */
val PlanDto.isTrialPlan: Boolean
    get() = name.contains("آزمایشی")

/**
 * مدت پلن بر حسب روز، برای مرتب‌سازی صعودی.
 *
 * سرور `duration_display` را همیشه به شکل "«عدد» روز" یا "«عدد» ماه" برمی‌گرداند
 * (accounting/serializers.py::PlanSerializer.get_duration_display) و خودِ بک‌اند هم
 * هر ماه را معادل ۳۰ روز حساب می‌کند (accounting/models.py::Plan.expiration date)،
 * پس این تبدیل با منطق سرور هم‌خوان است.
 */
val PlanDto.durationInDays: Int
    get() {
        val value = durationDisplay.trim().takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        return if ("ماه" in durationDisplay) value * 30 else value
    }

/** پلن‌ها را به ترتیب مورد نظر UI مرتب می‌کند: ابتدا پلن آزمایشی، سپس بقیه به ترتیب صعودی مدت. */
fun List<PlanDto>.sortedForBanner(): List<PlanDto> =
    sortedWith(compareByDescending<PlanDto> { it.isTrialPlan }.thenBy { it.durationInDays })
