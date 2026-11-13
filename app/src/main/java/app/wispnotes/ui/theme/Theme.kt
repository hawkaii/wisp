package app.wispnotes.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object WispColors {
    val Void = Color(0xFF0B0A0F)
    val Ink = Color(0xFF1F1A2A)
    val Purple = Color(0xFF5E3A94)
    val Kiro = Color(0xFF790ECB)
    val Lavender = Color(0xFFA28FC3)
    val Haze = Color(0xFFE6DCF5)
    val Pumpkin = Color(0xFFF67B25)
}

// Dark only: the design has no light theme.
private val Scheme = darkColorScheme(
    primary = WispColors.Pumpkin,
    onPrimary = WispColors.Void,
    secondary = WispColors.Purple,
    onSecondary = Color.White,
    tertiary = WispColors.Kiro,
    background = WispColors.Void,
    onBackground = Color.White,
    surface = WispColors.Ink,
    onSurface = Color.White,
    surfaceVariant = WispColors.Purple,
    onSurfaceVariant = WispColors.Haze,
    outline = WispColors.Lavender,
)

private val Bold = FontWeight.ExtraBold
private val WispTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 31.sp, fontWeight = Bold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = Bold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = Bold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = Bold),
)

private val WispShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
)

/** True when the system animation scale is 0 ("remove animations"): springs snap, nothing floats or flickers. */
val LocalReduceMotion = compositionLocalOf { false }

private fun reduceMotionNow(ctx: Context) =
    Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

@Composable
fun WispTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var reduce by remember { mutableStateOf(reduceMotionNow(ctx)) }
    LifecycleResumeEffect(Unit) {
        reduce = reduceMotionNow(ctx) // the setting is changed outside the app: read it again on resume
        onPauseOrDispose {}
    }
    CompositionLocalProvider(LocalReduceMotion provides reduce) {
        MaterialTheme(colorScheme = Scheme, typography = WispTypography, shapes = WispShapes) {
            CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
        }
    }
}

val Outline = BorderStroke(3.dp, Color.Black)

/** Flat sticker card: thick dark outline and a hard drop edge, no blur. */
@Composable
fun WispCard(
    modifier: Modifier = Modifier,
    color: Color = WispColors.Purple,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .drawBehind {
                drawRoundRect(
                    WispColors.Void,
                    topLeft = Offset(0f, 4.dp.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(22.dp.toPx()),
                )
            }
            .background(color, shape)
            .border(Outline, shape),
    ) { content() }
}
