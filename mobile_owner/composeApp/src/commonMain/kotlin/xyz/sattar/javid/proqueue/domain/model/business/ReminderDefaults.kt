package xyz.sattar.javid.proqueue.domain.model.business

/**
 * The lead time a reminder uses when nobody has chosen one.
 *
 * Thirty minutes, matching `Business.notification_minutes_before`'s default on
 * the server. It used to be 0 here (and 10/20 in [xyz.sattar.javid.proqueue.core.prefs.PreferencesManager],
 * which disagreed with each other), so a business created from the app pushed
 * `notification_minutes_before=0` to the backend and every reminder — local
 * notification and panel SMS alike — fired at the appointment time itself,
 * which is too late to be a reminder at all.
 */
const val DEFAULT_REMINDER_MINUTES = 30
