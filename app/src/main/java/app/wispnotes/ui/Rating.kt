package app.wispnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.wispnotes.R

/** Five orange stars with a ghost shield. Each star has a 48dp touch target. */
@Composable
fun GhostRating(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(painterResource(R.drawable.stk_shield), null, modifier = Modifier.size(64.dp), tint = Color.Unspecified)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 1..5) {
                Box(
                    Modifier
                        .size(48.dp)
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
                    Icon(
                        painterResource(R.drawable.stk_star),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).alpha(if (i <= value) 1f else 0.3f),
                        tint = Color.Unspecified,
                    )
                }
            }
        }
    }
}
