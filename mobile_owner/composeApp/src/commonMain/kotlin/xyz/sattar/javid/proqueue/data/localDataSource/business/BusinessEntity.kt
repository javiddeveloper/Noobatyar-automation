package xyz.sattar.javid.proqueue.data.localDataSource.business

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Business")
data class BusinessEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String? = null,
    val uniqueCode: String? = null,
    val phone: String,
    val address: String,
    val logoPath: String = "",
    val defaultServiceDuration: Int,
    val workStartHour: Int, // 0-23
    val workEndHour: Int, // 0-23
    val notificationEnabled: Boolean = true,
    val notificationTypes: String = "SMS", // "SMS,WHATSAPP,TELEGRAM"
    val notificationMinutesBefore: Int = 30,
    val createdAt: Long,
    val allowAnonymousView: Boolean = false,
    val notifyOwnerBySms: Boolean = true,
    val paymentMethod: String? = null,
    val acceptedPaymentMethods: String = "",
    val maxAppointmentsPerHour: Int? = null,
    val depositMode: String? = null,
    val depositAmount: Int? = null,
    val merchantId: String = "",
    val paymentLink: String = "",
    val cardNumber: String = "",
    val cardOwnerName: String = "",
    val bio: String = "",
    val moderationStatus: String? = null,
    val moderationStatusDisplay: String = "",
    val moderationNote: String = "",
    val moderationSubmittedAt: Long = 0
)