package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A purchasable add-on pack: SMS credit or appointment credit (legacy: feature). */
@Serializable
data class AddOnPackDto(
    val id: Int,
    val name: String,
    val price: Long,
    @SerialName("price_display") val priceDisplay: String,
    val kind: String, // "sms_pack" | "appointment_pack" | "feature"
    @SerialName("sms_amount") val smsAmount: Int = 0,
    @SerialName("appointment_amount") val appointmentAmount: Int = 0,
    @SerialName("feature_key") val featureKey: String = "",
    @SerialName("duration_days") val durationDays: Int = 0
)

@Serializable
data class BuyAddonRequestDto(
    @SerialName("pack_id") val packId: Int
)
