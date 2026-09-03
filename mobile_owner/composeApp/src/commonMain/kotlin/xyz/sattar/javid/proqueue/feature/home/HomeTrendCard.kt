package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils

/** Which period the trend card is showing. */
private enum class TrendPeriod(val label: String) {
    WEEKLY("هفتگی"),
    MONTHLY("ماهانه"),
}

/**
 * The appointment trend on Home: weekly or monthly, drawn as a line.
 *
 * ── Shape of the thing ──────────────────────────────────────────────────────
 *
 * Two levels of switching, because they answer different questions and
 * collapsing them into one row of chips made both harder to read:
 *
 *   هفتگی  — the last seven days. The day-to-day question: is this week busy.
 *   ماهانه — a whole Jalali month, with last / this / next selectable. The
 *            planning question, and the only one that can look forward.
 *
 * A line, not bars: at 31 points a bar per day is a picket fence, and the shape
 * of the trend — which is the actual information — reads far better as a curve.
 * The drawing is shared with nothing else on the screen so both periods look
 * like the same chart with different data (see [NeonLineCanvas]).
 *
 * Tapping "مشاهده" opens the visitors list over exactly the range on screen, so
 * the number in the card and the list behind it can never disagree.
 */
@Composable
fun HomeTrendCard(
    weeklyCounts: List<Int>,
    overview: MonthOverview?,
    onRangeClick: (start: Long, endExclusive: Long) -> Unit,
) {
    var period by remember { mutableStateOf(TrendPeriod.WEEKLY) }
    // Default to the current month: the common question is "how is this month
    // going", with the neighbours a tap away rather than a scroll away.
    var monthIndex by remember(overview?.current?.monthIndex) { mutableStateOf(1) }

    val selectedMonth = overview?.buckets?.getOrNull(monthIndex)

    // A month is plotted per week, not per day. Thirty-one points with a couple
    // of busy days apart is a row of needles — technically the data, visually
    // noise, and impossible to read a trend from. Weekly totals give four or
    // five points: an actual shape. The weekly period keeps day resolution,
    // which is the whole reason to look at a single week.
    val counts = when (period) {
        TrendPeriod.WEEKLY -> weeklyCounts
        TrendPeriod.MONTHLY -> selectedMonth?.dailyCounts.orEmpty().chunked(7).map { it.sum() }
    }
    val labels = when (period) {
        TrendPeriod.WEEKLY -> weeklyCounts.indices.map { i ->
            if (i == weeklyCounts.lastIndex) "امروز" else "${weeklyCounts.size - 1 - i} روز"
        }
        TrendPeriod.MONTHLY -> counts.indices.map { "هفته ${it + 1}" }
    }
    val total = counts.sum()
    val caption = when (period) {
        TrendPeriod.WEEKLY -> "نوبت در ۷ روز اخیر"
        TrendPeriod.MONTHLY -> "نوبت در ${selectedMonth?.label.orEmpty()}"
    }

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
                PeriodToggle(
                    selected = period,
                    onSelect = { period = it },
                )

                val range = when (period) {
                    TrendPeriod.WEEKLY -> weeklyRange()
                    TrendPeriod.MONTHLY -> selectedMonth?.let {
                        it.rangeStart to it.rangeEndExclusive
                    }
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = range != null && range.second > 0L) {
                            range?.let { onRangeClick(it.first, it.second) }
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
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

            // Month chips only in the monthly period — in the weekly one they
            // would select a range the chart is not showing.
            if (period == TrendPeriod.MONTHLY && overview != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    overview.buckets.forEachIndexed { index, bucket ->
                        MonthChip(
                            label = bucket.label,
                            total = bucket.total,
                            isSelected = index == monthIndex,
                            onClick = { monthIndex = index },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = total.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Spacer(Modifier.height(6.dp))

            AnimatedContent(
                targetState = counts to labels,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "trend",
            ) { (series, axis) ->
                Column {
                    NeonLineCanvas(counts = series)
                    if (series.size >= 2 && series.any { it > 0 }) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // Only the ends are labelled. A label per point at
                            // this width overlaps into an unreadable smear, and
                            // the two ends are what anchor the axis anyway.
                            Text(
                                text = axis.lastOrNull().orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                            Text(
                                text = axis.firstOrNull().orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The last seven days, as a (start, endExclusive) range. */
private fun weeklyRange(): Pair<Long, Long> {
    val dayMillis = 24L * 60 * 60 * 1000
    val todayStart = DateTimeUtils.startOfTodayMillis()
    return (todayStart - 6 * dayMillis) to (todayStart + dayMillis)
}

@Composable
private fun PeriodToggle(
    selected: TrendPeriod,
    onSelect: (TrendPeriod) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            TrendPeriod.entries.forEach { option ->
                val isSelected = option == selected
                Surface(
                    modifier = Modifier.clickable { onSelect(option) },
                    shape = RoundedCornerShape(9.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent,
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
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
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = total.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
