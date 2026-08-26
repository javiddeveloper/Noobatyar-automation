package xyz.sattar.javid.proqueue.data.localDataSource.visitor

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Visitor",
    indices = [Index(value = ["phoneNumber"], unique = true)]
)
data class VisitorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fullName: String,
    val phoneNumber: String,
    val createdAt: Long
)
