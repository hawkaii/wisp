package app.wispnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wispnotes.ui.theme.WispColors

/** Small sticker label. Clickable (with a 48dp touch target) when [onClick] is given. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WispChip(label: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    val touch = if (onClick != null) Modifier.minimumInteractiveComponentSize().pressable(onClick = onClick) else Modifier
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) WispColors.Void else Color.White,
        modifier = modifier
            .then(touch)
            .clip(shape)
            .background(if (selected) WispColors.Pumpkin else WispColors.Ink, shape)
            .border(2.dp, WispColors.Void, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Pill button with a spring press and a 48dp minimum height. [compact] fits three in a row. */
@Composable
fun WispButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = WispColors.Pumpkin,
    textColor: Color = WispColors.Void,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(shape)
            .background(color, shape)
            .border(2.dp, WispColors.Void, shape)
            .padding(horizontal = if (compact) 8.dp else 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = if (compact) 13.sp else 15.sp), color = textColor)
    }
}
