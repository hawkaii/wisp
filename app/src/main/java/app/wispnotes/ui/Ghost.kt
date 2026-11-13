package app.wispnotes.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.drawscope.scale
import app.wispnotes.ui.theme.LocalReduceMotion
import app.wispnotes.ui.theme.WispColors

/** The Wisp ghost, drawn in Canvas. Floats a few dp up and down when [float] is true. */
@Composable
fun Ghost(size: Dp = 96.dp, modifier: Modifier = Modifier, float: Boolean = true) {
    val bob by rememberInfiniteTransition(label = "ghost").animateFloat(
        initialValue = 0f,
        targetValue = if (float && !LocalReduceMotion.current) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Canvas(modifier.size(size)) {
        val pad = this.size.width * 0.08f
        val w = this.size.width - 2 * pad
        val h = this.size.height - 2 * pad - 6.dp.toPx()
        translate(pad, pad + (bob - 0.5f) * 6.dp.toPx()) {
            val r = w / 2
            val sw = w / 3
            val body = Path().apply {
                moveTo(0f, r)
                arcTo(Rect(0f, 0f, w, w), 180f, 180f, false)
                lineTo(w, h * 0.88f)
                for (i in 0 until 3) {
                    val x = w - sw * i
                    quadraticBezierTo(x - sw / 2, h * 1.12f, x - sw, h * 0.88f)
                }
                close()
            }
            drawPath(body, Color.White)
            drawPath(body, WispColors.Void, style = Stroke(w * 0.06f, join = StrokeJoin.Round))
            val eyeW = w * 0.06f
            val eyeH = w * 0.09f
            for (x in listOf(0.36f, 0.64f)) {
                drawOval(WispColors.Void, Offset(w * x - eyeW, r * 0.85f), Size(eyeW * 2, eyeH * 2))
            }
            for (x in listOf(0.2f, 0.8f)) {
                drawCircle(WispColors.Pumpkin, w * 0.05f, Offset(w * x, r * 1.15f))
            }
        }
    }
}

/** A small candle flame that flickers. Decoration only. */
@Composable
fun Flame(size: Dp = 24.dp, modifier: Modifier = Modifier) {
    val still = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "flame")
    val flick by t.animateFloat(
        initialValue = 0.85f,
        targetValue = if (still) 0.85f else 1.1f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "flick",
    )
    val sway by t.animateFloat(
        initialValue = -1f,
        targetValue = if (still) -1f else 1f,
        animationSpec = infiniteRepeatable(tween(730, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        translate(sway * w * 0.04f, 0f) {
            scale(1f, flick, pivot = Offset(w / 2, h)) {
                val flame = Path().apply {
                    moveTo(w / 2, 0f)
                    cubicTo(w * 0.95f, h * 0.45f, w * 0.85f, h, w / 2, h)
                    cubicTo(w * 0.15f, h, w * 0.05f, h * 0.45f, w / 2, 0f)
                    close()
                }
                drawPath(flame, WispColors.Pumpkin)
                drawOval(Color(0xFFFFD08A), Offset(w * 0.33f, h * 0.5f), Size(w * 0.34f, h * 0.42f))
            }
        }
    }
}
