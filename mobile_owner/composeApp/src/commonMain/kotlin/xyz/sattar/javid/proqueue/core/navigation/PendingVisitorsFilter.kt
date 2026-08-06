package xyz.sattar.javid.proqueue.core.navigation

import xyz.sattar.javid.proqueue.feature.home.VisitorsNavArgs

/**
 * Carries a one-shot filter from a Home stat card / queue row / chart tap
 * into the existing "آخرین نوبت‌ها" bottom tab.
 *
 * This deliberately does NOT flow through the nav route's own arguments.
 * MainTab.LastVisitors.route is a single fixed AppScreens.Visitors instance,
 * reused via the same popUpTo/launchSingleTop/restoreState "switch tab" call
 * the bottom bar itself uses — navigating with a *different* route instance
 * would either push a second, bottom-bar-less stacked copy of the screen
 * (which is what happened before this fix) or have its arguments silently
 * ignored by restoreState rehydrating the saved instance. A plain holder
 * lets Home set the desired filter right before switching tabs, and the tab
 * destination reads-and-clears it exactly once when it's (re)composed for
 * that navigation, regardless of how the user got there.
 */
object PendingVisitorsFilter {
    private var pending: VisitorsNavArgs? = null

    fun set(args: VisitorsNavArgs) {
        pending = args
    }

    fun consume(): VisitorsNavArgs? {
        val args = pending
        pending = null
        return args
    }
}
