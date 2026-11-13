package app.wispnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wispnotes.note.MdKind
import app.wispnotes.note.MdLine
import app.wispnotes.ui.theme.WispColors

/** `**bold**` becomes bold; everything else stays as typed. */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1 && i < parts.lastIndex) withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(part) }
        else append(if (i % 2 == 1) "**$part" else part)
    }
}

@Composable
fun MdLineView(line: MdLine, modifier: Modifier = Modifier) {
    val t = MaterialTheme.typography
    when (line.kind) {
        MdKind.H1 -> Text(inline(line.text), modifier, style = t.headlineMedium.copy(fontSize = 24.sp))
        MdKind.H2 -> Text(inline(line.text), modifier, style = t.titleMedium.copy(fontSize = 20.sp))
        MdKind.H3 -> Text(inline(line.text), modifier, style = t.titleMedium)
        MdKind.Bullet -> Row(modifier) {
            Text("•", style = t.bodyLarge, color = WispColors.Pumpkin)
            Text(inline(line.text), Modifier.padding(start = 8.dp), style = t.bodyLarge)
        }
        MdKind.Quote -> Row(modifier.fillMaxWidth()) {
            Box(Modifier.width(3.dp).height(20.dp).background(WispColors.Lavender))
            Text(inline(line.text), Modifier.padding(start = 10.dp), style = t.bodyLarge, color = WispColors.Lavender)
        }
        MdKind.Para -> Text(inline(line.text), modifier, style = t.bodyLarge, color = Color.White)
    }
}
