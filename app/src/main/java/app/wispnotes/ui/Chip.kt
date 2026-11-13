package app.wispnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wispnotes.ui.theme.WispColors

/** Small label, or a large purple choice when [choice] is true. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WispChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    choice: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    val touch = if (onClick != null) Modifier.minimumInteractiveComponentSize().pressable(onClick = onClick) else Modifier
    Text(
        label,
        style = if (choice) MaterialTheme.typography.bodyLarge.copy(fontWeight = MaterialTheme.typography.labelLarge.fontWeight) else MaterialTheme.typography.labelMedium,
        color = if (selected) WispColors.Void else Color.White,
        textAlign = TextAlign.Center,
        modifier = modifier
            .then(touch)
            .clip(shape)
            .background(if (selected) WispColors.Pumpkin else if (choice) WispColors.Purple else WispColors.Ink, shape)
            .border(if (choice) 3.dp else 2.dp, Color.Black, shape)
            .then(if (choice) Modifier.heightIn(min = 48.dp) else Modifier)
            .padding(horizontal = if (choice) 14.dp else 12.dp, vertical = if (choice) 6.dp else 6.dp),
    )
}

/** Main sticker button: 56dp tall, black outline, and a hard 4dp drop. */
@Composable
fun WispButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = WispColors.Pumpkin,
    textColor: Color = WispColors.Void,
    compact: Boolean = false,
    leadingIcon: Int? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .heightIn(min = if (compact) 48.dp else 56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(shape)
            .drawBehind {
                drawRoundRect(
                    Color.Black,
                    topLeft = Offset(0f, 4.dp.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                )
            }
            .background(color, shape)
            .border(3.dp, Color.Black, shape)
            .padding(horizontal = if (compact) 12.dp else 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            leadingIcon?.let { Icon(painterResource(it), null, tint = Color.Unspecified, modifier = Modifier.size(30.dp)) }
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = if (compact) 16.sp else 18.sp), color = textColor)
        }
    }
}
