package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared metrics for the floating glass bottom bar in [MainNavHost].
 *
 * The bar is ~80.dp tall with a home FAB that sticks above it; content must
 * clear that stack so the last items / FAB remain reachable when scrolling.
 */
object BottomBarDefaults {
    /** Extra scroll/FAB clearance so content sits above the floating bottom bar. */
    val ContentClearance: Dp = 140.dp

    /** FAB lift so it sits above the bar instead of under the home cutout. */
    val FabClearance: Dp = 100.dp
}

@Composable
fun BottomBarSpacer() {
    Spacer(modifier = Modifier.height(BottomBarDefaults.ContentClearance))
}
