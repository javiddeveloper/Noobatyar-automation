package xyz.sattar.javid.proqueue.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Colors
val PrimaryPurple = Color(0xFF8B5CF6)
val PrimaryPurpleDark = Color(0xFF7C3AED)

// Dark Theme Colors
val DarkBackground = Color(0xFF0F0F0F)
val DarkSurface = Color(0xFF1A1A1A)
val DarkSurfaceVariant = Color(0xFF2A2A2A)
val DarkOnSurface = Color(0xFFFFFFFF)
val DarkOnSurfaceVariant = Color(0xFF9CA3AF)
val DarkBorder = Color(0xFF374151)

// Light Theme Colors
val LightBackground = Color(0xFFF9FAFB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF3F4F6)
val LightOnSurface = Color(0xFF111827)
val LightOnSurfaceVariant = Color(0xFF6B7280)
val LightBorder = Color(0xFFE5E7EB)

// Floating bottom bar.
//
// Its own colours rather than surfaceContainerHigh: that token is shared with
// the shimmers, the stat cards and pull-to-refresh, so tuning it for the bar
// silently repaints all of them. The bar has a harder job than a card — it
// floats over scrolling content and has to stay legible as an object in its
// own right — so it gets a value picked for that alone.
//
// Two things have to hold at once, and earlier attempts each got only one.
//
// *Separation*: the bar must stand off the page (LightBackground #F9FAFB).
// #FFFFFF gave 1.04 contrast and #F0F0F0 gave 1.09 — a smudge, then a slightly
// better smudge. The dark bar reads correctly at 1.25 against its own page, so
// that is the reference; these sit at ~1.30.
//
// *Hue*: solving separation alone produced a neutral grey, which stood out as
// foreign next to an app that is purple throughout. Both values are now the
// brand purple (PrimaryPurple) carried to the required lightness, so the bar
// belongs to the theme instead of merely being visible in it.
//
// Icon contrast stays high on both (~12.8:1 dark-on-light, ~14.9:1 light-on-dark).
val LightBottomBar = Color(0xFFE1D5FD)
val DarkBottomBar = Color(0xFF2A2340)

// Common Colors
val ErrorColor = Color(0xFFEF4444)
val SuccessColor = Color(0xFF10B981)