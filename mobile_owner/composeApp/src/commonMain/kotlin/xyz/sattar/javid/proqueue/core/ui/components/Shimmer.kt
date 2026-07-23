package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.shimmer(
    shape: Shape = RoundedCornerShape(12.dp)
): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.55f)

    return this
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(offset - 400f, 0f),
                end = Offset(offset, 400f)
            )
        )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    Box(modifier = modifier.shimmer(shape))
}

@Composable
fun HomeDashboardShimmer() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Stats grid (2x2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerBox(
                modifier = Modifier.weight(1f).height(100.dp),
                shape = RoundedCornerShape(22.dp)
            )
            ShimmerBox(
                modifier = Modifier.weight(1f).height(100.dp),
                shape = RoundedCornerShape(22.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerBox(
                modifier = Modifier.weight(1f).height(100.dp),
                shape = RoundedCornerShape(22.dp)
            )
            ShimmerBox(
                modifier = Modifier.weight(1f).height(100.dp),
                shape = RoundedCornerShape(22.dp)
            )
        }
    }
}

@Composable
fun HomeChartShimmer() {
    ShimmerBox(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun HomeUsageShimmer() {
    ShimmerBox(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun HomeButtonShimmer() {
    ShimmerBox(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun HomePlanBannerShimmer() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShimmerBox(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            shape = RoundedCornerShape(20.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(3) {
                ShimmerBox(
                    modifier = Modifier.width(8.dp).height(8.dp),
                    shape = RoundedCornerShape(4.dp)
                )
                if (it < 2) Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
fun ListItemShimmer(height: Dp = 88.dp) {
    ShimmerBox(
        modifier = Modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun LastVisitorsListShimmer(count: Int = 6) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tabs placeholder
        ShimmerBox(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        repeat(count) {
            ListItemShimmer()
        }
    }
}
