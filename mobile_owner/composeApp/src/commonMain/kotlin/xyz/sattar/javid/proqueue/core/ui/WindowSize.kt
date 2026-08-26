package xyz.sattar.javid.proqueue.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Coarse width buckets for the adaptive layout (phone → tablet → desktop),
 * matching the breakpoint table in docs/OWNER_WEB_PLAN.md section ۸:
 * Compact < 600dp (today's phone UI, unchanged), Medium 600–1000dp (narrow
 * NavigationRail), Expanded > 1000dp (rail/drawer + width-capped content).
 */
enum class WindowSize {
    Compact,
    Medium,
    Expanded;

    companion object {
        fun of(width: Dp): WindowSize = when {
            width < 600.dp -> Compact
            width < 1000.dp -> Medium
            else -> Expanded
        }
    }
}

/**
 * Current width bucket, provided once from a root [androidx.compose.foundation.layout.BoxWithConstraints]
 * in App.kt. Defaults to [WindowSize.Compact] so components rendered outside
 * that root (e.g. previews) degrade to today's phone layout instead of
 * crashing — same pattern as [LocalHazeState].
 */
val LocalWindowSize = staticCompositionLocalOf { WindowSize.Compact }
