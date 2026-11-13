package app.wispnotes.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wispnotes.extract.ExtractWorker
import app.wispnotes.extract.Extracted
import app.wispnotes.extract.Extractors
import app.wispnotes.extract.OkHttpFetcher
import app.wispnotes.note.Note
import app.wispnotes.note.NoteDraft
import app.wispnotes.note.Source
import app.wispnotes.ui.Ghost
import app.wispnotes.ui.WispChip
import app.wispnotes.ui.theme.WispColors
import app.wispnotes.ui.theme.WispTheme
import app.wispnotes.vault.SafVaultRepository
import app.wispnotes.vault.VaultRepository
import coil.compose.AsyncImage
import java.net.URI
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Small Save sheet for ACTION_SEND text/plain. Writes a stub note into Inbox/, then WorkManager fills it in. */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val rawUrl = Source.extractUrl(text)
        val url = rawUrl?.let(Source::cleanUrl).orEmpty()
        // Title: subject, else the shared text without the link, else the host.
        val title = subject.ifBlank { text.replace(rawUrl ?: "", "").trim() }
            .ifBlank { runCatching { URI(url).host }.getOrNull().orEmpty() }
            .ifBlank { "Saved link" }
        val repo: VaultRepository = SafVaultRepository(this)

        setContent {
            WispTheme {
                Box(Modifier.fillMaxSize().clickable(role = Role.Button) { finish() }, contentAlignment = Alignment.BottomCenter) {
                    SaveSheet(url, title, repo) { finish() }
                }
            }
        }
    }
}

@Composable
private fun SaveSheet(url: String, initialTitle: String, repo: VaultRepository, close: () -> Unit) {
    var title by remember { mutableStateOf(initialTitle) }
    var titleEdited by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    // Preview only: the note itself is filled in by ExtractWorker after Save.
    var preview by remember { mutableStateOf<Extracted?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = remember(url) { Source.detect(url) }
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        val found = withContext(Dispatchers.IO) { Extractors.extract(url, OkHttpFetcher::get) }
        preview = found
        if (!titleEdited) found.title?.let { title = it }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = false) {}
            .clip(shape)
            .background(WispColors.Purple)
            .navigationBarsPadding()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Ghost(64.dp)
        Text("Save to Wisp", style = MaterialTheme.typography.titleMedium)
        Text("${info.source} · ${info.type}", color = WispColors.Haze)
        Preview(preview, loading = url.isNotBlank() && preview == null)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { info.tags.forEach { WispChip(it) } }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WispColors.Ink).padding(12.dp)) {
            BasicTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleEdited = true
                },
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(WispColors.Pumpkin),
            )
        }
        if (preview?.status == "failed") {
            // Link only: the page gave nothing (Instagram without a preview), so the user can paste the caption.
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WispColors.Ink).padding(12.dp)) {
                BasicTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(WispColors.Pumpkin),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (caption.isEmpty()) Text("Paste caption", color = WispColors.Lavender)
                        inner()
                    },
                )
            }
        }
        status?.let { Text(it, color = WispColors.Haze) }
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(WispColors.Pumpkin)
                .clickable(role = Role.Button) {
                    scope.launch {
                        if (!repo.hasAccess()) {
                            status = "Open Wisp and pick a vault first."
                            return@launch
                        }
                        val draft = NoteDraft(info, url, title.trim().ifEmpty { "Saved link" }, LocalDate.now(), caption)
                        repo.writeInboxNote(Note.fileName(draft), Note.render(draft))
                            .onSuccess { name ->
                                if (url.isNotBlank()) ExtractWorker.enqueue(ctx, name)
                                close()
                            }
                            .onFailure { status = "Could not save: ${it.message}" }
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Text("Save", style = MaterialTheme.typography.labelLarge, color = WispColors.Void) }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun Preview(found: Extracted?, loading: Boolean) {
    if (loading) {
        Text("Reading the page…", color = WispColors.Lavender)
        return
    }
    if (found == null || found.status == "failed") return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        found.thumbnail?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
        Column(Modifier.weight(1f)) {
            found.author?.let { Text(it, color = WispColors.Haze, maxLines = 1) }
            found.minutes?.let { Text("~$it min", color = WispColors.Haze) }
            found.text?.let { Text(it, color = WispColors.Lavender, maxLines = 2) }
        }
    }
}
