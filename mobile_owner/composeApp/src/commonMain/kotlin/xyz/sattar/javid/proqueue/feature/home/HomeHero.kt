package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.business.Business

/**
 * The pinned, collapsing head of the Home screen.
 *
 * ── Why it is pinned rather than just the first list item ───────────────────
 *
 * Home held six stacked sections and the two things an owner reaches for most
 * — opening the calendar and sending the booking link — were the fifth item
 * down and a tap into another tab. Both now live here, above everything, and
 * the section stays on screen while the rest scrolls under it.
 *
 * Collapsing (rather than a fixed bar) is what keeps that from costing
 * permanent vertical space: at rest it shows the date, today's count and two
 * full-width actions; scrolled, it folds down to a single compact row with the
 * same two actions as icon buttons. Driven by [collapseFraction] — a 0..1
 * value the caller derives from list scroll — so there is one source of truth
 * for the animation and no second scroll listener.
 */
@Composable
fun HomeHero(
    collapseFraction: Float,
    todayAppointmentsCount: Int?,
    business: Business?,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = 1f - collapseFraction.coerceIn(0f, 1f)
    val formattedDate = remember { DateTimeUtils.formatDate(DateTimeUtils.systemCurrentMilliseconds()) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "امروز، $formattedDate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (todayAppointmentsCount != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Event,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "$todayAppointmentsCount نوبت",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    // The collapsed affordances: they fade in exactly as the
                    // full-width buttons below fade out, so the actions are
                    // never unavailable mid-scroll.
                    if (collapseFraction > 0.05f) {
                        Spacer(Modifier.width(8.dp))
                        Row(
                            modifier = Modifier.alpha(collapseFraction),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CompactAction(
                                icon = Icons.Rounded.CalendarMonth,
                                onClick = onOpenCalendar,
                            )
                            business?.let { CompactBookingLinkAction(it) }
                        }
                    }
                }
            }

            // Expanded actions. Height is animated to 0 so the collapse is a
            // fold rather than a pop, and the row is dropped from composition
            // once invisible instead of being left behind at zero height.
            if (expanded > 0.02f) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((88.dp * expanded))
                        .alpha(expanded)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CalendarActionCard(
                        onClick = onOpenCalendar,
                        modifier = Modifier.weight(1f),
                    )
                    BookingLinkActionCard(
                        business = business,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(17.dp),
        )
    }
}

/** Copy-the-link action in its collapsed, icon-only form. */
@Composable
private fun CompactBookingLinkAction(business: Business) {
    val uniqueCode = business.uniqueCode ?: return
    val clipboard = LocalClipboardManager.current
    val link = "${xyz.sattar.javid.proqueue.BuildKonfig.BOOKING_BASE_URL}/b/$uniqueCode"
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                if (copied) Color(0xFF1B5E20) else MaterialTheme.colorScheme.primary
            )
            .clickable {
                clipboard.setText(buildAnnotatedString { append(link) })
                copied = true
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.Share,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(17.dp),
        )
    }
}

/** Opening the calendar, promoted to a primary action on Home. */
@Composable
private fun CalendarActionCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "تقویم و رزرو نوبت",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
            )
        }
    }
}

/** The booking link, moved up from the fifth section to the header. */
@Composable
private fun BookingLinkActionCard(
    business: Business?,
    modifier: Modifier = Modifier,
) {
    val uniqueCode = business?.uniqueCode
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }

    Card(
        modifier = modifier.clickable(enabled = uniqueCode != null) {
            val link = "${xyz.sattar.javid.proqueue.BuildKonfig.BOOKING_BASE_URL}/b/$uniqueCode"
            clipboard.setText(buildAnnotatedString { append(link) })
            copied = true
        },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (copied) Color(0xFF1B5E20) else MaterialTheme.colorScheme.primary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.Share,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = if (copied) "کپی شد!" else "لینک نوبت‌گیری",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}
