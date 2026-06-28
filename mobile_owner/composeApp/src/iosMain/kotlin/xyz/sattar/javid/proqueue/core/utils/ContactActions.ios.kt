package xyz.sattar.javid.proqueue.core.utils

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openUrl(url: String) {
    val encodedUrl = url.replace(" ", "%20")
    val nsUrl = NSURL.URLWithString(encodedUrl)
    if (nsUrl != null) {
        if (UIApplication.sharedApplication.canOpenURL(nsUrl)) {
            UIApplication.sharedApplication.openURL(nsUrl, emptyMap<Any?, Any>(), null)
        }
    }
}

actual fun openSms(phone: String, message: String?) {
    val body = if (message != null) "&body=" + message.replace(" ", "%20") else ""
    openUrl("sms:${formatPhoneNumberForAction(phone)}$body")
}

actual fun openWhatsApp(phone: String, message: String?) {
    val text = if (message != null) "?text=" + message.replace(" ", "%20") else ""
    openUrl("https://wa.me/${formatPhoneNumberForAction(phone)}$text")
}

actual fun openTelegram(phone: String, message: String?) {
    val text = if (message != null) "?url=&text=" + message.replace(" ", "%20") else ""
    openUrl("https://t.me/share/url$text")
}

actual fun openPhoneDial(phone: String) {
    openUrl("tel:${formatPhoneNumberForAction(phone)}")
}

actual fun openInstagram(username: String) {
    openUrl("https://instagram.com/$username")
}

actual fun openTwitter(username: String) {
    openUrl("https://twitter.com/$username")
}
