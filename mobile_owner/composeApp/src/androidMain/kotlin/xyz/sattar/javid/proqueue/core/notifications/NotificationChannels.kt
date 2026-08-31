package xyz.sattar.javid.proqueue.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * The single channel every appointment notification is posted on — the local
 * alarm reminder, the FCM reminder and the new-booking push alike — so the
 * owner has one switch in the system settings rather than three that mean
 * roughly the same thing.
 *
 * [ensureCreated] runs from [xyz.sattar.javid.proqueue.ProQueueApp.onCreate],
 * not lazily at the point of showing a notification, and that is the whole
 * point. When the app is in the background the FCM SDK posts the tray
 * notification *itself*, using the `default_notification_channel_id` declared
 * in AndroidManifest.xml — our own code never runs. Android drops a
 * notification whose channel does not exist, without an error anywhere, so an
 * app that only created the channel on its first locally-shown notification
 * silently swallowed every push until then. On a fresh install that meant
 * every push, forever, since nothing else creates it first.
 *
 * The id must stay in sync with two other places: the manifest meta-data, and
 * `settings.FCM_ANDROID_CHANNEL_ID` on the server.
 */
object NotificationChannels {

    const val APPOINTMENT_REMINDERS = "appointment_reminders"

    /** Idempotent: creating an existing channel updates its name/description
     *  and leaves any importance the user has since changed alone. */
    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                APPOINTMENT_REMINDERS,
                "یادآوری نوبت‌ها",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "یادآوری نوبت‌های نزدیک و اطلاع از نوبت‌های جدید"
            }
        )
    }
}
