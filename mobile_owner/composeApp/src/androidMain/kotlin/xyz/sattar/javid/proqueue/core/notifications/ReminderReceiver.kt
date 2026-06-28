package xyz.sattar.javid.proqueue.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import xyz.sattar.javid.proqueue.R

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ProQueue::ReminderWakeLock"
            )
        wakeLock.acquire(3000)
        val customerName = intent.getStringExtra("customerName") ?: ""
        val businessName = intent.getStringExtra("businessName") ?: ""
        val minutesBefore = intent.getIntExtra("minutesBefore", 10)
        val businessId = intent.getLongExtra("businessId", -1)
        val visitorId = intent.getLongExtra("visitorId", -1)
        Log.d("ReminderReceiver", "onReceive called at ${System.currentTimeMillis()}")
        showNotification(context, customerName, businessName, minutesBefore, businessId, visitorId)
        wakeLock.release()
    }

    private fun showNotification(
        context: Context,
        customerName: String,
        businessName: String,
        minutesBefore: Int,
        businessId: Long,
        visitorId: Long
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "appointment_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Appointment Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for upcoming appointments"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Create PendingIntent for notification click
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("from_notification", true)
            putExtra("businessId", businessId)
            putExtra("visitorId", visitorId)
            putExtra("customerName", customerName)
            putExtra("businessName", businessName)
            putExtra("minutesBefore", minutesBefore)
            putExtra("openMessageDialog", true)
        }
        
        val pendingIntent = if (intent != null) {
            android.app.PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        // Using system default icon if specific resource is not found to avoid compilation errors
        // In a real app, use R.drawable.ic_notification or similar
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.main_icon)
            .setContentTitle("یادآوری نوبت")
            .setContentText("نوبت $customerName در $businessName تا $minutesBefore دقیقه دیگر نزدیک هست برای اطلاع رسانی نوبت اینجا را لمس کنید")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("نوبت $customerName در $businessName تا $minutesBefore دقیقه دیگر نزدیک هست برای اطلاع رسانی نوبت اینجا را لمس کنید")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
