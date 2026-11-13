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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wispnotes.R
import app.wispnotes.extract.ExtractWorker
import app.wispnotes.extract.Extracted
import app.wispnotes.extract.Extractors
import app.wispnotes.extract.OkHttpFetcher
import app.wispnotes.note.Note
import app.wispnotes.note.NoteDraft
import app.wispnotes.note.Source
import app.wispnotes.ui.Ghost
import app.wispnotes.ui.WispButton
import app.wispnotes.ui.WispChip
import app.wispnotes.ui.theme.WispColors
import app.wispnotes.ui.theme.WispTheme
import app.wispnotes.vault.SafVaultRepository
import app.wispnotes.vault.VaultRepository
import coil.compose.AsyncImage
import java.net.URI
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Save sheet for ACTION_SEND text/plain. It writes one Markdown note into Inbox/. */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val rawUrl = Source.extractUrl(text)
        val url = rawUrl?.let(Source::cleanUrl).orEmpty()
        val sharedText = text.replace(rawUrl ?: "", "").trim()
        val title = subject.ifBlank { sharedText }
        val repo: VaultRepository = SafVaultRepository(this)

        setContent {
            WispTheme {
                Box(
                    Modifier.fillMaxSize().background(Color(0xD90B0A0F)).clickable(role = Role.Button) { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    SaveSheet(url, title, repo) { finish() }
                }
            }
        }
    }
}

@Composable
private fun SaveSheet(url: String, initialTitle: String, repo: VaultRepository, close: () -> Unit) {
    var title by remember { mutableStateOf(initialTitle) }
    var titleEdited by remember { mutableStateOf(initialTitle.isNotBlank()) }
    var caption by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Extracted?>(null) }
    var saving by remember { mutableStateOf(false) }
    var caught by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = remember(url) { Source.detect(url) }
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        val quick = withContext(Dispatchers.IO) {
            if (info.source == "youtube") Extractors.youtubeQuick(url, OkHttpFetcher::get)
            else Extractors.extract(url, OkHttpFetcher::get)
        }
        preview = quick
        if (!titleEdited) quick.title?.let { title = it }
        if (info.source == "youtube") {
            val full = withContext(Dispatchers.IO) { Extractors.extract(url, OkHttpFetcher::get) }
            preview = Extracted(
                title = full.title ?: quick.title,
                author = full.author ?: quick.author,
                thumbnail = full.thumbnail ?: quick.thumbnail,
                minutes = full.minutes,
                text = full.text,
            )
            if (!titleEdited) preview?.title?.let { title = it }
        }
    }

    LaunchedEffect(caught) {
        if (caught) {
            delay(3_000)
            close()
        }
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
        if (caught) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Ghost(64.dp)
                androidx.compose.material3.Icon(painterResource(R.drawable.stk_link), null, tint = Color.Unspecified, modifier = Modifier.size(48.dp))
            }
            Text("Caught it!", style = MaterialTheme.typography.headlineMedium)
            Text(
                "\"${title.ifBlank { preview?.title ?: fallbackTitle(url) }}\" is in the pot.",
                color = WispColors.Haze,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                info.tags.forEach { WispChip("#$it") }
                preview?.minutes?.let { WispChip("~$it min") }
            }
            WispButton("Done", close, Modifier.fillMaxWidth())
            Text("Closes by itself in 3 s", color = WispColors.Haze, style = MaterialTheme.typography.bodySmall)
        } else {
            Ghost(64.dp)
            Text("Save to Wisp", style = MaterialTheme.typography.titleMedium)
            Text("${info.source} · ${info.type}", color = WispColors.Haze)
            Preview(preview, loading = url.isNotBlank() && preview == null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { info.tags.forEach { WispChip("#$it") } }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WispColors.Ink).padding(12.dp)) {
                BasicTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleEdited = true
                    },
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(WispColors.Pumpkin),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (title.isEmpty()) Text("Reading the page…", color = WispColors.Lavender)
                        inner()
                    },
                )
            }
            if (preview?.status == "failed") {
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
            WispButton(
                label = if (saving) "Saving…" else "Save",
                onClick = {
                    if (!saving) {
                        scope.launch {
                        saving = true
                        if (!titleEdited && title.isBlank() && url.isNotBlank()) {
                            withTimeoutOrNull(3_000) {
                                while (preview?.title == null) delay(40)
                            }
                        }
                        if (!repo.hasAccess()) {
                            saving = false
                            status = "Open Wisp and pick a vault first."
                            return@launch
                        }
                        val savedTitle = title.trim().ifEmpty { preview?.title ?: fallbackTitle(url) }
                        val draft = NoteDraft(info, url, savedTitle, LocalDate.now(), caption)
                        repo.writeInboxNote(Note.fileName(draft), Note.render(draft))
                            .onSuccess { name ->
                                if (url.isNotBlank()) ExtractWorker.enqueue(ctx, name)
                                title = savedTitle
                                caught = true
                            }
                            .onFailure { status = "Could not save: ${it.message}" }
                        saving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
            )
            Spacer(Modifier.height(2.dp))
        }
    }
}

private fun fallbackTitle(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull()
    val queryId = uri?.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.substringAfter('=')
    val pathId = uri?.path?.trim('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() && it != "watch" }
    val id = queryId ?: pathId
    return listOf(Source.detect(url).type, id).filter { !it.isNullOrBlank() }.joinToString(" ").ifBlank { "Saved link" }
}

@Composable
private fun Preview(found: Extracted?, loading: Boolean) {
    if (loading) {
        Text("Reading the page…", color = WispColors.Haze)
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
            found.text?.let { Text(it, color = WispColors.Haze, maxLines = 2) }
        }
    }
}
