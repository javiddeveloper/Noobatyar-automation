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

    /** Whether the content review alone allows public visibility.
     *
     * Deliberately NOT the full answer — a business can be APPROVED and still
     * hidden because of the billing lock. Callers that need the real answer
     * must use [Business.isPubliclyVisible], which also checks `isLocked`.
     */
    val isPubliclyVisible: Boolean get() = this == APPROVED

    /**
     * Editing the business and saving will actually resubmit it for review.
     *
     * REJECTED only. The backend's `resubmit_if_content_changed`
     * (business/services.py) deliberately excludes SUSPENDED — a moderator
     * suspended the listing as an enforcement action, and letting the owner
     * lift that by retyping their title would defeat the point of suspending
     * it. An owner-app button offering "edit and resubmit" on a SUSPENDED
     * business would do nothing when pressed.
     */
    val needsOwnerAction: Boolean get() = this == REJECTED

    /** SUSPENDED can't be self-resolved by editing; the owner needs support. */
    val requiresSupportContact: Boolean get() = this == SUSPENDED

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
