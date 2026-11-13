package app.wispnotes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.wispnotes.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay

/** Clickable that springs down a little while pressed. Plain click when the user asked for reduced motion. */
fun Modifier.pressable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) 0.95f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/** List entrance: fades and rises, each row a little after the one above. Only the first screenful is staggered. */
fun Modifier.staggerIn(index: Int): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val t = remember { Animatable(if (reduce || index > 8) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (t.value < 1f) {
            delay(index * 50L)
            t.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
        }
    }
    graphicsLayer {
        alpha = t.value.coerceIn(0f, 1f)
        translationY = (1f - t.value) * 24.dp.toPx()
    }
}
