package xyz.sattar.javid.proqueue.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState

/**
 * A single [HazeState] shared across the app shell so the top app bar and the
 * bottom navigation bar (which live in different scaffolds) can both blur the
 * same scrolling content. Provided in MainNavHost; the scrolling NavHost is the
 * haze *source*, the bars are haze *effects*.
 *
 * A default instance is provided so components used outside the shell (auth /
 * business-selection flows) still render — they just have no source to sample,
 * which degrades gracefully to a plain translucent tint.
 */
val LocalHazeState = staticCompositionLocalOf { HazeState() }
