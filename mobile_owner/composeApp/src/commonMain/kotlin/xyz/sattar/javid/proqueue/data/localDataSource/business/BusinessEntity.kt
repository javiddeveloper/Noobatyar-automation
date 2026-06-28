package xyz.sattar.javid.proqueue.data.localDataSource.business

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Business")
data class BusinessEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val phone: String,
    val address: String,
    val logoPath: String = "",
    val defaultServiceDuration: Int,
    val workStartHour: Int, // 0-23
    val workEndHour: Int, // 0-23
    val notificationEnabled: Boolean = true,
    val notificationTypes: String = "SMS", // "SMS,WHATSAPP,TELEGRAM"
    val notificationMinutesBefore: Int = 30,
    val createdAt: Long
)