package app.momento.fotos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import app.momento.fotos.R

object Palette {
    val Bg = Color(0xFF121214)          // gris casi negro, estilo editor
    val Surface = Color(0xFF1B1C1F)
    val Surface2 = Color(0xFF232428)
    val Surface3 = Color(0xFF2C2D32)
    val Line = Color(0xFF34363C)
    val Text = Color(0xFFF2F3F5)
    val TextDim = Color(0xFF9A9DA6)
    val TextFaint = Color(0xFF666A73)
    val Sky = Color(0xFF58C7F3)          // celeste principal
    val SkyDeep = Color(0xFF2FA8DD)
    val SkySoft = Color(0x2658C7F3)
    val OnSky = Color(0xFF04202C)
    val Danger = Color(0xFFFF6B6B)
}

val Display = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)
val Body = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.8).sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp),
)

@Composable
fun MomentoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Sky,
            onPrimary = Palette.OnSky,
            background = Palette.Bg,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.Surface2,
            onSurfaceVariant = Palette.TextDim,
            outline = Palette.Line,
            error = Palette.Danger,
        ),
        typography = typography,
        content = content,
    )
}
