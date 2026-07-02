package xyz.sattar.javid.proqueue.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.yakan_bold
import proqueue.composeapp.generated.resources.yakan_medium
import proqueue.composeapp.generated.resources.yekan_regular

@Composable
actual fun yakanFontFamily() = FontFamily(
    Font(Res.font.yekan_regular, FontWeight.Normal),
    Font(Res.font.yakan_medium, FontWeight.Medium),
    Font(Res.font.yakan_bold, FontWeight.Bold)
)