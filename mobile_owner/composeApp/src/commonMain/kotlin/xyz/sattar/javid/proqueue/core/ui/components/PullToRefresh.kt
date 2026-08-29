package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.main_icon

private val PullThreshold = 72.dp
private val MaxPull = 130.dp
private val IndicatorSize = 44.dp

/**
 * Pull-to-refresh that pushes the whole page down as you drag, revealing a small
 * Noobatyar badge with a progress ring above it.
 *
 * The label tracks the gesture: "برای بروزرسانی بکشید" while short of the
 * threshold, "رها کنید" once far enough, and "در حال بروزرسانی…" while the
 * refresh runs. Releasing past the threshold calls [onRefresh]; the page stays
 * held open until [isRefreshing] goes back to false.
 *
 * Replaces the toolbar refresh buttons that used to sit next to the profile
 * avatar on the home / appointments screens.
 *
 * From [WindowSize.Medium] up, a small floating refresh button is layered on
 * top of the content instead of relying on the pull gesture. The gesture
 * itself is drag-based (see the [NestedScrollConnection] below): a mouse
 * wheel at the top of a list doesn't produce the same overscroll deltas a
 * touch drag does, and even where it technically could, "pull down to
 * refresh" isn't a gesture desktop users reach for — nobody click-drags page
 * content down the way they'd swipe a phone. The nested-scroll wiring stays
 * active either way (harmless if unused), so nothing about the phone path
 * changes.
 */
@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorTopPadding: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { PullThreshold.toPx() }
    val maxPullPx = with(density) { MaxPull.toPx() }
    // Must fit the ring *and* the label text below it (icon + padding + label
    // line is ~66dp) — a smaller resting height let the page content, which
    // sits right below the indicator, draw over and clip the bottom of the
    // label once the pull settled here after release.
    val restingPx = with(density) { (IndicatorSize + 30.dp).toPx() }

    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    // Only a refresh the *user* pulled for holds the page open. Screens also set
    // isRefreshing during their initial load, and reacting to that would make the
    // page slide open on its own the first time it is opened.
    var userTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing, userTriggered) {
        if (!userTriggered) return@LaunchedEffect
        if (isRefreshing) {
            if (offset.value < restingPx) offset.animateTo(restingPx)
        } else {
            // The ViewModel may not have flipped isRefreshing on yet; this effect
            // restarts if it does, so we only collapse when it stays off.
            delay(400)
            offset.animateTo(0f)
            userTriggered = false
        }
    }

    // Read through a snapshot so the remembered connection never sees a stale
    // value of isRefreshing.
    val refreshing by rememberUpdatedState(isRefreshing)

    val connection = remember(thresholdPx, maxPullPx, restingPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Dragging back up first gives back whatever pull is open, so the
                // list doesn't start scrolling while the page is still pushed down.
                if (refreshing) return Offset.Zero
                if (available.y < 0 && offset.value > 0f) {
                    val consumed = -minOf(offset.value, -available.y)
                    scope.launch { offset.snapTo((offset.value + consumed).coerceAtLeast(0f)) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (refreshing) return Offset.Zero
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                // Rubber-band: the further it is open, the less each pixel moves it.
                val resistance = 1f - (offset.value / maxPullPx).coerceIn(0f, 0.85f)
                val next = (offset.value + available.y * 0.5f * resistance)
                    .coerceIn(0f, maxPullPx)
                scope.launch { offset.snapTo(next) }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (refreshing || offset.value <= 0f) return Velocity.Zero
                if (offset.value >= thresholdPx) {
                    userTriggered = true
                    offset.animateTo(restingPx)
                    currentOnRefresh()
                } else {
                    offset.animateTo(0f)
                }
                // Swallow the fling so the list doesn't also fling from the pull.
                return Velocity(0f, available.y)
            }
        }
    }

    Box(modifier = modifier.nestedScroll(connection)) {
        PullIndicator(
            offset = offset.value,
            thresholdPx = thresholdPx,
            isRefreshing = isRefreshing && userTriggered,
            topPadding = indicatorTopPadding
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = offset.value }
        ) {
            content()
        }

        if (LocalWindowSize.current != WindowSize.Compact) {
            DesktopRefreshButton(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                topPadding = indicatorTopPadding,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

/**
 * The desktop stand-in for the pull gesture — see the class doc above for
 * why. Positioned top-end (the left edge under the app's forced RTL) so it
 * doesn't collide with anything a screen already puts at the visual start of
 * its content area.
 */
@Composable
private fun DesktopRefreshButton(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    topPadding: Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(top = topPadding + 12.dp, end = 12.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp
    ) {
        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "بروزرسانی",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PullIndicator(
    offset: Float,
    thresholdPx: Float,
    isRefreshing: Boolean,
    topPadding: Dp
) {
    if (offset <= 0.5f && !isRefreshing) return

    val progress = (offset / thresholdPx).coerceIn(0f, 1f)
    val readyToRelease = offset >= thresholdPx

    val spin by rememberInfiniteTransition(label = "pull-spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .height(with(LocalDensity.current) { offset.toDp() }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(IndicatorSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .alpha(if (isRefreshing) 1f else (0.35f + progress * 0.65f)),
            contentAlignment = Alignment.Center
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(IndicatorSize - 6.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .size(IndicatorSize - 6.dp)
                        .rotate(if (readyToRelease) spin else 0f),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }

            // The tiny Noobatyar mark in the middle of the ring.
            Image(
                painter = painterResource(Res.drawable.main_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .scale(if (isRefreshing) 1f else 0.7f + progress * 0.3f)
            )
        }

        if (offset > thresholdPx * 0.35f || isRefreshing) {
            Text(
                text = when {
                    isRefreshing -> "در حال بروزرسانی…"
                    readyToRelease -> "رها کنید"
                    else -> "برای بروزرسانی بکشید"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (readyToRelease || isRefreshing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
