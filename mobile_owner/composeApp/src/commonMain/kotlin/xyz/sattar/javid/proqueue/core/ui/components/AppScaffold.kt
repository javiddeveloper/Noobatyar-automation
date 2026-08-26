package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize

/**
 * Content width caps for the adaptive layout. These are not one number
 * because "don't stretch" means something different per kind of screen: a
 * single-column form reads badly long before a data-dense dashboard does.
 */
object ContentWidth {
    /** Single-column forms — login, register, OTP. Beyond this a text field
     *  becomes a 1900px ribbon, which is what this whole cap exists to stop. */
    val Form = 420.dp

    /** Card/list screens — business picker, settings. */
    val List = 760.dp

    /** Data-dense screens — dashboard, queue, calendar. */
    val Wide = 1100.dp
}

/**
 * Shared wrapper for a screen's main content (see docs/OWNER_WEB_PLAN.md
 * section ۸, item 3): centers content and caps its width so it doesn't
 * stretch edge-to-edge on a wide monitor.
 *
 * The cap applies from [WindowSize.Medium] up, not only [WindowSize.Expanded]
 * — a 900dp-wide window is already wide enough for a full-bleed login form to
 * look broken. [WindowSize.Compact] is a pass-through, so phone layout is
 * untouched.
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    maxWidth: androidx.compose.ui.unit.Dp = ContentWidth.Wide,
    content: @Composable BoxScope.() -> Unit
) {
    if (LocalWindowSize.current == WindowSize.Compact) {
        Box(modifier = modifier, content = content)
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
            Box(modifier = Modifier.widthIn(max = maxWidth).fillMaxSize(), content = content)
        }
    }
}
