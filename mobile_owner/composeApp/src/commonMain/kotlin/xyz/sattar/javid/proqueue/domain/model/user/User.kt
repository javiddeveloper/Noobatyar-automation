package xyz.sattar.javid.proqueue.domain.model.user

data class User(
    val id: Int,
    val phone: String,
    val name: String,
    val userType: String,
    val isEmployee: Boolean,
    val joinedAt: String
)