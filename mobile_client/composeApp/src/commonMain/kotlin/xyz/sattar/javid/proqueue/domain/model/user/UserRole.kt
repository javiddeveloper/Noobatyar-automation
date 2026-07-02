package xyz.sattar.javid.proqueue.domain.model.user

enum class UserRole(val value: String, val persianName: String) {
    BUSINESS_OWNER("BUSINESS_OWNER", "صاحب کسب‌وکار"),
    CLIENT("CLIENT", "مشتری"),
    ADMIN("ADMIN", "مدیر"),
    UNKNOWN("UNKNOWN", "نامشخص");

    companion object {
        fun fromString(value: String?): UserRole {
            return entries.find { it.value == value } ?: UNKNOWN
        }
    }
}
