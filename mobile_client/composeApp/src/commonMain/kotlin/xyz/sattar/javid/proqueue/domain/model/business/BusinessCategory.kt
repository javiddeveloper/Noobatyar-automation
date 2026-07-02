package xyz.sattar.javid.proqueue.domain.model.business

enum class BusinessCategory(val value: String, val persianName: String) {
    BEAUTY_SALON("BEAUTY_SALON", "آرایشگاه و سالن زیبایی"),
    DOCTOR("DOCTOR", "پزشک و کلینیک"),
    CONSULTANT("CONSULTANT", "مشاوره"),
    OTHER("OTHER", "سایر");

    companion object {
        fun fromString(value: String?): BusinessCategory {
            return entries.find { it.value == value } ?: OTHER
        }
    }
}
