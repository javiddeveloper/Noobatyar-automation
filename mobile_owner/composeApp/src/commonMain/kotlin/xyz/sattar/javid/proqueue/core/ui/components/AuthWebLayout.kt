package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize

/**
 * The web/desktop shell shared by every auth screen (login, register, OTP,
 * reset): a full-viewport two-column split — brand panel on one side, the
 * screen's own form on the other.
 *
 * Shared rather than repeated per screen so the whole sign-in flow reads as
 * one surface: navigating from login to register should change the form, not
 * the page.
 *
 * Deliberately not the phone screen in a narrow column, and not a small card
 * floating in the middle either — at 1920x1080 a 420dp card leaves most of the
 * screen empty, which reads as unfinished rather than minimal.
 *
 * There is no TopAppBar (an app bar over a single form is a mobile tell) and
 * no bottomBar (a full-width button welded to the bottom of a 1080px-tall
 * viewport is the clearest tell of a stretched phone UI). Screens put their
 * actions at the end of [content] instead.
 *
 * RTL: the app forces [androidx.compose.ui.unit.LayoutDirection.Rtl]
 * (ui/theme/Theme.kt), so the Row's first child lands on the *right*. The form
 * goes first because that is where a Persian reader starts.
 */
@Composable
fun AuthWebLayout(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Form column. Weighted rather than fixed so it keeps its share of
            // an ultrawide monitor; the inner widthIn stops the fields
            // themselves from stretching inside it.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = content
                )
            }

            // Brand panel. Hidden below Expanded: at ~700dp wide, splitting the
            // viewport leaves the form column too narrow to be comfortable, so
            // Medium gets the form full-width instead.
            if (LocalWindowSize.current == WindowSize.Expanded) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFA78BFA),
                                    Color(0xFF8B5CF6),
                                    Color(0xFF7C3AED)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.padding(48.dp)
                    ) {
                        NoobatyarMark(modifier = Modifier.size(120.dp))
                        Text(
                            text = "نوبت‌یار",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "نوبت‌ها و صف کسب‌وکارت را یکجا مدیریت کن.",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
