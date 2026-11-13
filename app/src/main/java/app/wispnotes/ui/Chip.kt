package app.wispnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.wispnotes.ui.theme.WispColors

/** Small sticker label. Clickable when [onClick] is given. */
@Composable
fun WispChip(label: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) WispColors.Void else Color.White,
        modifier = modifier
            .clip(shape)
            .background(if (selected) WispColors.Pumpkin else WispColors.Ink, shape)
            .border(2.dp, WispColors.Void, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
