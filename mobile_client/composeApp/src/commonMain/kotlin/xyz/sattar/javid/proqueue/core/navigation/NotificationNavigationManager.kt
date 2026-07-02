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
}
