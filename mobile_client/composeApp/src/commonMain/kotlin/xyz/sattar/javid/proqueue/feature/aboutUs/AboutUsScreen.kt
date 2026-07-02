package xyz.sattar.javid.proqueue.feature.aboutUs

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
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import proqueue.composeapp.generated.resources.*

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
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // App Info Card
            SettingsCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(Res.drawable.main_icon),
                        contentDescription = stringResource(Res.string.about_us_app_name),
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(24.dp))
                    )
                    Text(
                        text = stringResource(Res.string.about_us_app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(Res.string.about_us_app_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Description Card
            SettingsCard {
                Text(
                    text = stringResource(Res.string.about_us_description),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Social Media Card
            SettingsCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.about_us_follow_us),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        maxItemsInEachRow = 3
                    ) {
                        SocialItem(
                            title = stringResource(Res.string.social_bale),
                            painter = painterResource(Res.drawable.bale),
                            onClick = { uriHandler.openUri("https://ble.ir/noobatyar") }
                        )
                        SocialItem(
                            title = stringResource(Res.string.social_eitaa),
                            painter = painterResource(Res.drawable.eitaa),
                            onClick = { uriHandler.openUri("https://eitaa.com/noobatyar") }
                        )
                        SocialItem(
                            title = stringResource(Res.string.social_rubika),
                            painter = painterResource(Res.drawable.rubika),
                            onClick = { uriHandler.openUri("https://rubika.ir/noobatyar") }
                        )
                        SocialItem(
                            title = stringResource(Res.string.instagram),
                            painter = painterResource(Res.drawable.instagram),
                            onClick = { uriHandler.openUri("https://instagram.com/ajviddev") }
                        )
                        SocialItem(
                            title = stringResource(Res.string.social_website),
                            painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.Language),
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = { uriHandler.openUri("https://noobatyar.ir") }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
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
private fun SocialItem(
    title: String,
    painter: Painter,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(
            painter = painter,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
