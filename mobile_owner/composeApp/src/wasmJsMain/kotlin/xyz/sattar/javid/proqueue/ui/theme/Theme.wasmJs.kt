package xyz.sattar.javid.proqueue.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.browser.document
import org.w3c.dom.HTMLMetaElement

// There's no status bar to theme on the web — the closest equivalent is the
// browser's own chrome color, set via <meta name="theme-color">. Values match
// ui/theme/Color.kt exactly (see docs/OWNER_WEB_PLAN.md section 7.3): dark
// background #0F0F0F, light background #F9FAFB.
//
// NOTE on the parameter: despite the name, Theme.kt calls this with
// `!isDarkTheme` — i.e. [isDark] is true when the *status bar/chrome* should
// render in its light-background appearance, mirroring how the Android actual
// feeds the same value straight into `isAppearanceLightStatusBars`. So
// isDark == true means the *app* is in light mode.
@Composable
actual fun SystemAppearance(isDark: Boolean) {
    LaunchedEffect(isDark) {
        val color = if (isDark) "#F9FAFB" else "#0F0F0F"
        val meta = document.querySelector("meta[name=theme-color]") as? HTMLMetaElement
        meta?.content = color
    }
}
