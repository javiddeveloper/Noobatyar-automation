package xyz.sattar.javid.proqueue.feature.aboutUs

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import proqueue.composeapp.generated.resources.*
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.ui.components.AppScaffold
import xyz.sattar.javid.proqueue.core.ui.components.ContentWidth
import xyz.sattar.javid.proqueue.core.utils.AppInfo

/**
 * Hand-picked violet ramp for the hero.
 *
 * Deliberately NOT MaterialTheme.colorScheme.secondary/tertiary: this app's
 * theme only overrides `primary`, so those two roles fall back to Material3's
 * baseline defaults (a grey-purple and a brownish pink) and any gradient built
 * from them comes out muddy brown instead of the brand violet.
 */
private val HeroViolets = listOf(
    Color(0xFF6D28D9),
    Color(0xFF8B5CF6),
    Color(0xFFA855F7),
    Color(0xFF7C3AED)
)

/** Converts ASCII digits to Persian digits for display. */
private fun String.toPersianDigits(): String =
    map { c -> if (c in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[c - '0'] else c }.joinToString("")

@Composable
fun AboutUsScreen(
    onNavigateBack: () -> Unit
) {
    AboutUsContent(
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun AboutUsContent(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit
) {
    if (LocalWindowSize.current == WindowSize.Compact) {
        AboutUsPhoneContent(modifier, onNavigateBack)
    } else {
        AboutUsWebContent(modifier, onNavigateBack)
    }
}

/** The contact/social list card shared by [AboutUsPhoneContent] and
 *  [AboutUsWebContent], so the two layouts can't drift apart on which
 *  platforms are listed or in what order. */
@Composable
private fun ContactCard(uriHandler: androidx.compose.ui.platform.UriHandler) {
    SettingsCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.about_us_follow_us),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SocialRow(
                title = stringResource(Res.string.social_bale),
                handle = "ble.ir/noobatyar",
                painter = painterResource(Res.drawable.ic_social_bale),
                tileColors = listOf(Color(0xFF10D19E), Color(0xFF059669)),
                index = 0,
                onClick = { uriHandler.openUri("https://ble.ir/noobatyar") }
            )
            SocialDivider()
            SocialRow(
                title = stringResource(Res.string.social_eitaa),
                handle = "eitaa.com/noobatyar",
                painter = painterResource(Res.drawable.ic_social_eitaa),
                tileColors = listOf(Color(0xFFFB923C), Color(0xFFEA6D0E)),
                index = 1,
                onClick = { uriHandler.openUri("https://eitaa.com/noobatyar") }
            )
            SocialDivider()
            SocialRow(
                title = stringResource(Res.string.social_rubika),
                handle = "rubika.ir/noobatyar",
                painter = painterResource(Res.drawable.ic_social_rubika),
                tileColors = listOf(Color(0xFF4ABDC9), Color(0xFF126AA1)),
                index = 2,
                onClick = { uriHandler.openUri("https://rubika.ir/noobatyar") }
            )
            SocialDivider()
            SocialRow(
                title = stringResource(Res.string.instagram),
                handle = "@javiddev",
                painter = painterResource(Res.drawable.ic_social_instagram),
                // Instagram's mark is defined by its gradient, so the
                // official ramp lives on the tile and the glyph is white.
                tileColors = listOf(
                    Color(0xFFFEDA75),
                    Color(0xFFFA7E1E),
                    Color(0xFFD62976),
                    Color(0xFF962FBF),
                    Color(0xFF4F5BD5)
                ),
                index = 3,
                onClick = { uriHandler.openUri("https://instagram.com/javiddev") }
            )
            SocialDivider()
            SocialRow(
                title = stringResource(Res.string.social_website),
                handle = "noobatyar.ir",
                painter = painterResource(Res.drawable.ic_social_website),
                tileColors = listOf(Color(0xFFA855F7), Color(0xFF6D28D9)),
                index = 4,
                onClick = { uriHandler.openUri("https://noobatyar.ir") }
            )
        }
    }
}

/** The description card shared by both layouts. */
@Composable
private fun DescriptionCard() {
    SettingsCard {
        Text(
            text = stringResource(Res.string.about_us_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Justify,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.6f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Phone layout — unchanged. A single scrolling column: hero, description,
 *  then the contact list. See [AboutUsWebContent] for the desktop split. */
@Composable
private fun AboutUsPhoneContent(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.about_us_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AmbientBackdrop(modifier = Modifier.matchParentSize())

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                HeroCard()
                DescriptionCard()

                // Contact list.
                //
                // Was a 3-per-row FlowRow of icon+label tiles, which left five
                // items sitting as 3 + an orphan row of 2, and showed nothing but
                // the platform name. A vertical list divides evenly at any count
                // and has room for the actual handle, which is the part someone
                // reading this screen is usually looking for.
                ContactCard(uriHandler = uriHandler)

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Desktop layout. A wall of Persian body text spanning 1900px is unreadable,
 * so this is width-capped like every other screen — but at [WindowSize.Expanded]
 * there's enough room to stop stacking everything in one column: the hero sits
 * beside the description+contact stack instead of on top of it, so the page
 * reads as two panels rather than one long scroll. Under the app's forced RTL
 * the hero (the *first* child of the Row) lands on the right, which is where a
 * page's lead visual belongs in an RTL reading order; the text content follows
 * to its left.
 *
 * At [WindowSize.Medium] there isn't enough room for a side-by-side split
 * without squeezing the hero or wrapping the social rows' handles, so it
 * stays a single, width-capped column — same stack as phone, just centred
 * instead of edge-to-edge.
 */
@Composable
private fun AboutUsWebContent(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val isExpanded = LocalWindowSize.current == WindowSize.Expanded

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.about_us_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AmbientBackdrop(modifier = Modifier.matchParentSize())

            AppScaffold(
                modifier = modifier.fillMaxSize(),
                maxWidth = if (isExpanded) ContentWidth.Wide else ContentWidth.List
            ) {
                if (isExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            HeroCard()
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            DescriptionCard()
                            ContactCard(uriHandler = uriHandler)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        HeroCard()
                        DescriptionCard()
                        ContactCard(uriHandler = uriHandler)

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

/**
 * Two oversized, very soft violet glows drifting behind the page so it reads as
 * alive rather than frozen. Drawn with drawBehind (not offset Boxes) so nothing
 * can ever affect layout or bleed a hard edge.
 */
@Composable
private fun AmbientBackdrop(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing)),
        label = "drift-1"
    )
    val drift2 by transition.animateFloat(
        initialValue = (2 * PI).toFloat(),
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(34000, easing = LinearEasing)),
        label = "drift-2"
    )

    val glow = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier.drawBehind {
            val w = size.width
            val h = size.height

            fun blob(cx: Float, cy: Float, radius: Float, alpha: Float) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = alpha), glow.copy(alpha = 0f)),
                        center = Offset(cx, cy),
                        radius = radius
                    ),
                    radius = radius,
                    center = Offset(cx, cy)
                )
            }

            // Centres stay well inside the viewport and radii stay large: a
            // circle centred off-screen gets clipped to a slab with a visible
            // straight edge, which reads as a rendering artifact rather than light.
            blob(
                cx = w * 0.34f + w * 0.12f * cos(drift1),
                cy = h * 0.24f + h * 0.06f * sin(drift1),
                radius = w * 0.95f,
                alpha = 0.10f
            )
            blob(
                cx = w * 0.68f + w * 0.11f * cos(drift2),
                cy = h * 0.74f + h * 0.07f * sin(drift2),
                radius = w * 1.00f,
                alpha = 0.09f
            )
        }
    )
}

@Composable
private fun HeroCard() {
    val transition = rememberInfiniteTransition(label = "hero")

    // The gradient itself slides back and forth. Reverse (not Restart) means it
    // can never "snap" at the loop point, and drawing it via drawBehind — which
    // always fills the exact card bounds — avoids the corner-leak you get from
    // rotating an oversized plate behind a rounded card.
    val gradientPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient-phase"
    )

    // A soft light band sweeping across the card, like a sheen on glass.
    val sheen by transition.animateFloat(
        initialValue = -0.45f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "sheen"
    )

    val floatOffset by transition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    val haloScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo-scale"
    )

    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing)),
        label = "orbit"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    // Slide the gradient endpoints by up to a full card width.
                    val dx = (gradientPhase - 0.5f) * w
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = HeroViolets,
                            start = Offset(-w * 0.3f + dx, 0f),
                            end = Offset(w * 1.3f + dx, h)
                        )
                    )
                }
                .drawWithContent {
                    drawContent()
                    val w = size.width
                    val h = size.height
                    val band = w * 0.22f
                    val cx = w * sheen
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.16f),
                                Color.Transparent
                            ),
                            start = Offset(cx - band, 0f),
                            end = Offset(cx + band, h)
                        )
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Sparkles orbiting the app mark.
                    Canvas(modifier = Modifier.size(158.dp)) {
                        val r = size.minDimension / 2f * 0.94f
                        val c = Offset(size.width / 2f, size.height / 2f)
                        repeat(6) { i ->
                            val a = ((orbit + i * 60f) * PI / 180.0)
                            val twinkle = ((sin((orbit * 2.4 + i * 70f) * PI / 180.0) + 1.0) / 2.0).toFloat()
                            drawCircle(
                                color = Color.White.copy(alpha = 0.20f + 0.45f * twinkle),
                                radius = (1.6f + 2.2f * twinkle).dp.toPx(),
                                center = Offset(
                                    c.x + r * cos(a).toFloat(),
                                    c.y + r * sin(a).toFloat()
                                )
                            )
                        }
                    }

                    // Breathing halo behind the icon.
                    Box(
                        modifier = Modifier
                            .size(122.dp)
                            .graphicsLayer {
                                scaleX = haloScale
                                scaleY = haloScale
                            }
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.30f),
                                        Color.White.copy(alpha = 0f)
                                    )
                                )
                            )
                    )

                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .graphicsLayer { translationY = floatOffset }
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.main_icon),
                            contentDescription = stringResource(Res.string.about_us_app_name),
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )
                    }
                }

                Text(
                    text = stringResource(Res.string.about_us_app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = stringResource(Res.string.about_us_app_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center
                )
                Surface(
                    color = Color.White.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "${stringResource(Res.string.app_version)} ${AppInfo.versionName.toPersianDigits()}",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

@Composable
private fun SocialDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 60.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

@Composable
private fun SocialRow(
    title: String,
    handle: String,
    painter: Painter,
    tileColors: List<Color>,
    index: Int,
    onClick: () -> Unit
) {
    // Same sheen as the hero card, staggered down the list so it reads as one
    // highlight travelling through the section rather than five separate blinks.
    val transition = rememberInfiniteTransition(label = "social-$index")
    val sheen by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "sheen-$index"
    )
    val phase = ((sheen + index * 0.12f) % 1f) * 1.9f - 0.45f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(tileColors))
                .drawWithContent {
                    drawContent()
                    val w = size.width
                    val band = w * 0.35f
                    val cx = w * phase
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.28f),
                                Color.Transparent
                            ),
                            start = Offset(cx - band, 0f),
                            end = Offset(cx + band, size.height)
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painter,
                contentDescription = title,
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                // Wrapped in an LTR isolate (U+2066 … U+2069). Handles are Latin
                // but sit in an RTL paragraph, so leading/trailing neutrals get
                // reordered to the paragraph edge — "@javiddev" rendered as
                // "javiddev@". The isolate pins the run to LTR without changing
                // where the block itself sits.
                text = "⁦$handle⁩",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp)
        )
    }
}
