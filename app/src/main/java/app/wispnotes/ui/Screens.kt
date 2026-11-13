package app.wispnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.wispnotes.ui.theme.WispCard
import app.wispnotes.ui.theme.WispColors

@Composable
fun HomeScreen() = Placeholder(
    "Home",
    "Nothing queued yet.",
    "Share a link to Wisp from any app. It lands here as a note.",
)

@Composable
fun ReviewScreen() = Placeholder(
    "Review",
    "Nothing to revisit.",
    "Notes you finish come back here after 3, 7, 21 and 60 days.",
)

@Composable
fun NotesScreen() = Placeholder(
    "Notes",
    "No notes yet.",
    "Every note is one Markdown file in your vault. Obsidian opens them too.",
)

@Composable
private fun Placeholder(title: String, headline: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        WispCard(Modifier.fillMaxWidth(), color = WispColors.Ink) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Ghost(120.dp)
                Spacer(Modifier.height(12.dp))
                Text(headline, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(body, color = WispColors.Lavender, textAlign = TextAlign.Center)
            }
        }
    }
}
