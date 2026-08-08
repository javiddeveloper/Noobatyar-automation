package xyz.sattar.javid.proqueue.domain.model.business

/**
 * Content-moderation state of a business, mirroring the backend's
 * `Business.MODERATION_STATUS_CHOICES`.
 *
 * A business is only listed publicly while it is [APPROVED]; everything else
 * means clients cannot find it yet. This is deliberately kept separate from
 * `is_locked` (subscription/plan limits) — the two answer different questions
 * for the owner ("not reviewed yet" vs. "your plan lapsed").
 */
enum class ModerationStatus(val value: String, val persianName: String) {
    PENDING("PENDING", "در انتظار تأیید"),
    APPROVED("APPROVED", "تأیید شده"),
    REJECTED("REJECTED", "تأیید نشده"),
    SUSPENDED("SUSPENDED", "تعلیق شده");

    /** Whether clients can currently see this business. */
    val isPubliclyVisible: Boolean get() = this == APPROVED

    /** The owner has to fix something and resubmit. */
    val needsOwnerAction: Boolean get() = this == REJECTED || this == SUSPENDED

    companion object {
        /**
         * Returns `null` for an unknown/absent value on purpose: a backend that
         * doesn't send `moderation_status` yet should leave the moderation UI
         * hidden rather than have us guess a state and mislabel the business.
         */
        fun fromString(value: String?): ModerationStatus? =
            entries.find { it.value.equals(value, ignoreCase = true) }
    }
}
