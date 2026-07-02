package xyz.sattar.javid.proqueue.domain.model.visitor

data class Visitor(
    val id: Long = 0,
    val fullName: String,
    val phoneNumber: String,
    val createdAt: Long
)