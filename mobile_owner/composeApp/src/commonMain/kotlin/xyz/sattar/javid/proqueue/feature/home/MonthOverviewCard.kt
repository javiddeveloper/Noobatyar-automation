package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Last month / this month / next month, in one card.
 *
 * ── Why this shape ──────────────────────────────────────────────────────────
 *
 * The ask was for somewhere on Home to see the month behind and the month
 * ahead, with no fixed idea of how. Three separate cards would cost three
 * screens of scroll on a screen that is already too long, and a single number
 * per month answers "how many" while hiding the thing an owner actually acts
 * on — *which days* are full and which are empty.
 *
 * So: one card, three chips, and a bar per day of the selected month. Tapping
 * a chip switches the month in place (no navigation, no scroll); tapping the
 * card opens the visitors list over exactly the range being shown. The bars
 * make a quiet month visibly quiet, which is the point of looking ahead.
 *
 * The next-month bars are the useful half: they are bookings that have not
 * happened yet, which is why the daily-counts endpoint had to grow a forward
 * window (see appointment/views/daily_counts_view.py).
 */
@Composable
fun MonthOverviewCard(
    overview: MonthOverview,
    onRangeClick: (start: Long, endExclusive: Long) -> Unit,
) {
    // Default to the current month: the common question is "how is this month
    // going", with the neighbours a tap away rather than a scroll away.
    var selectedIndex by remember(overview.current.monthIndex) { mutableStateOf(1) }
    val buckets = overview.buckets
    val selected = buckets.getOrElse(selectedIndex) { overview.current }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "روند ماهانه",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = selected.rangeEndExclusive > 0L) {
                            onRangeClick(selected.rangeStart, selected.rangeEndExclusive)
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "مشاهده",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Month chips. Labels are the real Persian month names rather than
            // "گذشته/آینده" so the card still makes sense at a glance in a
            // screenshot or after a week away from the app.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                buckets.forEachIndexed { index, bucket ->
                    MonthChip(
                        label = bucket.label,
                        total = bucket.total,
                        isSelected = index == selectedIndex,
                        onClick = { selectedIndex = index },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                },
            ) { bucket ->
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = bucket.total.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "نوبت در ${bucket.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    MonthBars(counts = bucket.dailyCounts)
                }
            }
        }
    }
}

@Composable
private fun MonthChip(
    label: String,
    total: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = total.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * One thin bar per day, scaled to the month's own busiest day.
 *
 * Scaled per-month rather than across all three: the question each bar answers
 * is "how does this day compare to a normal day *this* month", and a single
 * exceptional day in another month would otherwise flatten everything here to
 * an unreadable line.
 */
@Composable
private fun MonthBars(counts: List<Int>) {
    if (counts.isEmpty()) {
        Text(
            text = "نوبتی ثبت نشده",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        return
    }

    val max = counts.max().coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        counts.forEach { count ->
            val fraction by animateFloatAsState(
                targetValue = (count.toFloat() / max).coerceIn(0f, 1f),
                animationSpec = tween(400),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    // A floor of 8% so an empty day still reads as a day on the
                    // axis instead of vanishing and making the month look short.
                    .height((44.dp * (0.08f + fraction * 0.92f)))
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (count == 0) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                    ),
            )
        }
    }
}
