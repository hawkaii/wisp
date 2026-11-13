package app.wispnotes.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.wispnotes.ui.theme.LocalReduceMotion

/** Five ghosts. The chosen ones light up with a springy pop and a tick of haptics. */
@Composable
fun GhostRating(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val reduce = LocalReduceMotion.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..5) {
            val on = i <= value
            val pop by animateFloatAsState(
                targetValue = if (on) 1f else 0.8f,
                animationSpec = if (reduce) snap<Float>() else spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium),
                label = "pop",
            )
            val glow by animateFloatAsState(if (on) 1f else 0.3f, label = "glow")
            Box(
                Modifier
                    .size(56.dp)
                    .pressable(role = Role.RadioButton) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onChange(i)
                    }
                    .semantics {
                        contentDescription = "Rate $i of 5"
                        selected = i == value
                    },
                contentAlignment = Alignment.Center,
            ) {
                Ghost(44.dp, Modifier.graphicsLayer { scaleX = pop; scaleY = pop; alpha = glow }, float = false)
            }
        }
    }
}
