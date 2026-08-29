package xyz.sattar.javid.proqueue.core.notifications

import xyz.sattar.javid.proqueue.core.permissions.jsHasNotificationPermission

// A real no-op, not a debt — see docs/OWNER_WEB_PLAN.md section 10.3. Local
// alarms only ever covered appointments created inside this same app process
// staying open, which a browser tab cannot promise; server-side push (FCM,
// section 10.1, a later phase) covers every business with notifications
// enabled regardless of where the appointment was created, so this scheduler
// having nothing to do is the correct end state, not a gap.
class WebNotificationScheduler : NotificationScheduler {
    override fun scheduleReminder(
        appointmentId: Long,
        customerName: String,
        businessName: String,
        triggerAtMillis: Long,
        minutesBefore: Int,
        businessId: Long,
        visitorId: Long
    ) {
        // No-op: see class kdoc.
    }

    override fun cancelReminder(appointmentId: Long) {
        // No-op: see class kdoc.
    }

    // Was hardcoded false — meaning the Notifications screen's toggle always
    // reported "not granted" even after the browser had already granted it,
    // and every "enable" toggle detoured through a permission prompt it
    // didn't need. Reads the real, already-decided browser state instead.
    override suspend fun hasPermission(): Boolean = jsHasNotificationPermission()
}
