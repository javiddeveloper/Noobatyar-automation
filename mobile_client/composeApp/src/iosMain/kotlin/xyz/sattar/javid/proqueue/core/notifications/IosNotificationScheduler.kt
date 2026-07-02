package xyz.sattar.javid.proqueue.core.notifications

import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.UserNotifications.UNNotificationPresentationOptionAlert
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptionBadge
import platform.UserNotifications.UNNotification
import platform.darwin.NSObject
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

import platform.UserNotifications.UNNotificationResponse
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIApplication

class IosNotificationScheduler : NotificationScheduler {
    
    init {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.delegate = object : NSObject(), UNUserNotificationCenterDelegateProtocol {
            override fun userNotificationCenter(
                center: UNUserNotificationCenter,
                willPresentNotification: UNNotification,
                withCompletionHandler: (platform.UserNotifications.UNNotificationPresentationOptions) -> Unit
            ) {
                withCompletionHandler(
                    UNNotificationPresentationOptionAlert or 
                    UNNotificationPresentationOptionSound or 
                    UNNotificationPresentationOptionBadge
                )
            }

            override fun userNotificationCenter(
                center: UNUserNotificationCenter,
                didReceiveNotificationResponse: UNNotificationResponse,
                withCompletionHandler: () -> Unit
            ) {
                val userInfo = didReceiveNotificationResponse.notification.request.content.userInfo
                val businessId = userInfo["businessId"] as? Long ?: -1L
                val visitorId = userInfo["visitorId"] as? Long ?: -1L
                val customerName = userInfo["customerName"] as? String ?: ""
                val businessName = userInfo["businessName"] as? String ?: ""
                val minutesBefore = userInfo["minutesBefore"] as? Int ?: 0

                val message = "نوتیفیکیشن: $customerName - $businessName ($minutesBefore دقیقه قبل) [Biz: $businessId, Vis: $visitorId]"
                
                // Show Alert on Main Thread
                platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
                    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
                    if (rootViewController != null) {
                        val alert = UIAlertController.alertControllerWithTitle(
                            title = "اطلاعات نوتیفیکیشن",
                            message = message,
                            preferredStyle = platform.UIKit.UIAlertControllerStyleAlert
                        )
                        alert.addAction(
                            UIAlertAction.actionWithTitle(
                                title = "باشه",
                                style = UIAlertActionStyleDefault,
                                handler = null
                            )
                        )
                        rootViewController.presentViewController(alert, animated = true, completion = null)
                    }
                }
                
                withCompletionHandler()
            }
        }
    }

    override fun scheduleReminder(
        appointmentId: Long,
        customerName: String,
        businessName: String,
        triggerAtMillis: Long,
        minutesBefore: Int,
        businessId: Long,
        visitorId: Long
    ) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        
        val content = UNMutableNotificationContent().apply {
            setTitle("یادآوری نوبت")
            setBody("نوبت $customerName در $businessName تا $minutesBefore دقیقه دیگر نزدیک هست برای اطلاع رسانی نوبت اینجا را لمس کنید")
            setSound(UNNotificationSound.defaultSound())
            setUserInfo(
                mapOf(
                    "businessId" to businessId,
                    "visitorId" to visitorId,
                    "customerName" to customerName,
                    "businessName" to businessName,
                    "minutesBefore" to minutesBefore
                )
            )
        }

        val timeInterval = (triggerAtMillis - DateTimeUtils.systemCurrentMilliseconds()) / 1000.0

        val safeTimeInterval = when {
            timeInterval <= 0 -> {
                println("Notification time has passed, showing immediately")
                1.0
            }
            timeInterval < 1.0 -> 1.0
            else -> timeInterval
        }

        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = safeTimeInterval,
            repeats = false
        )

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = appointmentId.toString(),
            content = content,
            trigger = trigger
        )

        center.addNotificationRequest(request) { error ->
            if (error != null) {
                println("Error scheduling notification: ${error.localizedDescription}")
            } else {
                println("Notification scheduled successfully for interval: $safeTimeInterval seconds")
            }
        }
    }

    override fun cancelReminder(appointmentId: Long) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(appointmentId.toString()))
    }

    override suspend fun hasPermission(): Boolean = suspendCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            cont.resume(settings?.authorizationStatus == UNAuthorizationStatusAuthorized)
        }
    }
}
