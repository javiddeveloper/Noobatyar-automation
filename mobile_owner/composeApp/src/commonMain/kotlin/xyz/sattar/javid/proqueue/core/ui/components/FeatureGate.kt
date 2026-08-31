package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.jetbrains.compose.resources.stringResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.coming_soon_badge
import proqueue.composeapp.generated.resources.coming_soon_hint
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementsResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto

/**
 * Three outcomes, in priority order:
 *  1. the server lists the capability in `coming_soon` — it has no
 *     implementation yet, so it renders inert with a «به‌زودی» badge no matter
 *     what the user's plan grants (an upgrade would buy nothing);
 *  2. the plan grants it — render the real controls;
 *  3. otherwise — the inline upgrade card.
 *
 * Shared by every screen that gates a feature behind a plan tier
 * (originally lived only in AdvancedSettingsTabs.kt, extracted here once
 * NotificationsScreen needed the exact same lock/upgrade treatment for
 * push notifications — same UI, must not drift between call sites).
 */
@Composable
fun FeatureGate(
    entitlements: EntitlementsResponseDto?,
    plans: List<PlanDto>,
    featureKey: String,
    title: String,
    description: String,
    onUpgrade: (Int) -> Unit,
    unlockedContent: @Composable () -> Unit
) {
    val comingSoon = entitlements?.isComingSoon(featureKey) == true
    val unlocked = entitlements?.hasFeature(featureKey) == true
    when {
        comingSoon -> ComingSoonFeatureCard(title, description)
        unlocked -> unlockedContent()
        else -> LockedFeatureCard(plans, featureKey, title, description, onUpgrade)
    }
}

/**
 * A capability the plan ladder advertises but the app cannot do yet. Rendered
 * dimmed and with no clickable surface at all, so there is no toggle to flip and
 * no upgrade button promising something that wouldn't work.
 */
@Composable
private fun ComingSoonFeatureCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        )
    ) {
        Column(modifier = Modifier.padding(16.dp).alpha(0.6f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.HourglassEmpty,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = stringResource(Res.string.coming_soon_badge),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(Res.string.coming_soon_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LockedFeatureCard(
    plans: List<PlanDto>,
    featureKey: String,
    title: String,
    description: String,
    onUpgrade: (Int) -> Unit
) {
    val unlockingPlan = remember(plans, featureKey) { findUnlockingPlan(plans, featureKey) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (unlockingPlan != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "این قابلیت با پلن «${unlockingPlan.name}» فعال می‌شود",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onUpgrade(unlockingPlan.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ارتقا به ${unlockingPlan.name} · ${unlockingPlan.priceDisplay}")
                }
            }
        }
    }
}

/** Cheapest active plan whose features unlock [featureKey], or null if none do (or plans not loaded yet). */
private fun findUnlockingPlan(plans: List<PlanDto>, featureKey: String): PlanDto? {
    return plans
        .filter { plan ->
            (plan.features[featureKey] as? JsonPrimitive)?.booleanOrNull == true
        }
        .minByOrNull { it.discountPrice ?: it.price }
}
