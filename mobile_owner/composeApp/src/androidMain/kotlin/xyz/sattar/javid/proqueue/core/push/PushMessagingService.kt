package xyz.sattar.javid.proqueue.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import xyz.sattar.javid.proqueue.R
import xyz.sattar.javid.proqueue.domain.usecase.push.SyncPushTokenUseCase

/**
 * Receives the reminder and new-booking notifications the backend sends through
 * FCM (see `api/services/push.py`).
 *
 * The payload deliberately carries both a `notification` block and a `data`
 * block: the system tray renders the former on its own while the app is in the
 * background, and [onMessageReceived] only runs for the foreground case — so
 * the notification is built here too, from the same fields, rather than the app
 * going quiet whenever it happens to be open.
 */
class PushMessagingService : FirebaseMessagingService(), KoinComponent {

    private val syncPushToken: SyncPushTokenUseCase by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Same channel the local alarm reminders use, so the owner has one switch in
     * the system settings for "appointment reminders" instead of two that mean
     * roughly the same thing. Must match settings.FCM_ANDROID_CHANNEL_ID on the
     * server, or Android 8+ drops the notification without a trace.
     */
    private val channelId = "appointment_reminders"

    /**
     * FCM rotates tokens (reinstall, restored backup, cleared app data). Without
     * this the server would keep addressing an address that no longer resolves,
     * and the owner would simply stop getting notifications with nothing to
     * explain it.
     */
    override fun onNewToken(token: String) {
        scope.launch { syncPushToken() }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "نوبت‌یار"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return

        showNotification(title, body, message.data)
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Appointment Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Reminders for upcoming appointments" }
            )
        }

        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("from_notification", true)
            data["business_id"]?.toLongOrNull()?.let { putExtra("businessId", it) }
            data["visitor_id"]?.toLongOrNull()?.let { putExtra("visitorId", it) }
            data["appointment_id"]?.toLongOrNull()?.let { putExtra("appointmentId", it) }
        }

        val pendingIntent = launch?.let {
            PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.main_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
