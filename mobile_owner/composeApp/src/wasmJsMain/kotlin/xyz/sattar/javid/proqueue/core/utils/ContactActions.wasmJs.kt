package xyz.sattar.javid.proqueue.core.utils

import kotlinx.browser.window

// docs/OWNER_WEB_PLAN.md section 4, row 5: on desktop browsers `sms:`/`tel:`
// are inert, and the breakpoint-aware "copy number" / wa.me fallback it
// describes is UI polish for a later phase (ReminderActionSheet, section
// 10.2) — out of scope for this MVP, which has no screen on the
// login/business-list path that calls these. Routing everything through
// window.open keeps every one of these a real, harmless call instead of a
// TODO() that would crash if some shared code path reaches it, and is
// correct as-is on mobile web (Safari/Chrome on a phone do open sms:/tel:).
private fun openInNewTab(url: String) {
    window.open(url, "_blank")
}

actual fun openSms(phone: String, message: String?) {
    val body = if (message.isNullOrBlank()) "" else "?body=" + jsEncodeURIComponent(message)
    openInNewTab("sms:${formatPhoneNumberForAction(phone)}$body")
}

actual fun openWhatsApp(phone: String, message: String?) {
    val encoded = if (message.isNullOrBlank()) "" else "?text=" + jsEncodeURIComponent(message)
    openInNewTab("https://wa.me/${formatPhoneNumberForAction(phone)}$encoded")
}

actual fun openTelegram(phone: String, message: String?) {
    val encoded = if (message.isNullOrBlank()) "" else "?url=&text=" + jsEncodeURIComponent(message)
    openInNewTab("https://t.me/share/url$encoded")
}

actual fun openPhoneDial(phone: String) {
    openInNewTab("tel:${formatPhoneNumberForAction(phone)}")
}

actual fun openUrl(url: String) {
    openInNewTab(url)
}

actual fun openInstagram(username: String) {
    openInNewTab("https://instagram.com/$username")
}

actual fun openTwitter(username: String) {
    openInNewTab("https://twitter.com/$username")
}

private fun jsEncodeURIComponent(value: String): String = encodeURIComponent(value)

// wasmJs has no `js("...")` dynamic escape hatch (that's Kotlin/JS-only) —
// binding the browser global as an `external` top-level function is the
// supported way to reach it.
private external fun encodeURIComponent(component: String): String
