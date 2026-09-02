package xyz.sattar.javid.proqueue.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object NotificationNavigationManager {
    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent = _navigationEvent.asStateFlow()

    fun navigate(event: NavigationEvent) {
        _navigationEvent.value = event
    }

    fun consumeEvent() {
        _navigationEvent.value = null
    }
}

sealed class NavigationEvent {
    data class ToVisitorDetails(val visitorId: Long, val openMessageDialog: Boolean = false) : NavigationEvent()

    /**
     * A destination named by a `noobatyar://…` deep link — today only from a
     * promotional campaign sent out of the admin panel (backend
     * `core/campaigns.py`), which lets marketing point a notification at a
     * screen without an app release.
     *
     * Deliberately a small closed set rather than "navigate to any route
     * string": the panel is edited by people who do not build the app, so an
     * arbitrary route would let a typo — or a route removed in a later
     * version — become a crash on tap. Anything unrecognised is dropped by
     * [parseDeepLink] and the notification just opens the app normally.
     */
    data class ToDeepLink(val target: DeepLinkTarget) : NavigationEvent()
}

enum class DeepLinkTarget {
    HOME,
    VISITORS,
    NOTIFICATIONS,
    SMS_REPORT,
    ADD_ONS,
    SETTINGS,
}

/**
 * Maps a deep-link host to a known screen, or null when it names nothing this
 * build understands.
 *
 * Kept in commonMain next to the enum so the accepted vocabulary is defined
 * once, and stays greppable from the panel side: these hosts are exactly the
 * values documented in the campaign compose form's hint text.
 */
fun parseDeepLink(host: String?): DeepLinkTarget? = when (host?.lowercase()) {
    "home" -> DeepLinkTarget.HOME
    "visitors", "customers" -> DeepLinkTarget.VISITORS
    "notifications" -> DeepLinkTarget.NOTIFICATIONS
    "sms-report", "smsreport" -> DeepLinkTarget.SMS_REPORT
    "addons", "add-ons", "subscription", "plans" -> DeepLinkTarget.ADD_ONS
    "settings" -> DeepLinkTarget.SETTINGS
    else -> null
}
