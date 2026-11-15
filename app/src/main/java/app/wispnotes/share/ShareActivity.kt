package app.wispnotes.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wispnotes.MainActivity
import app.wispnotes.R
import app.wispnotes.extract.ExtractWorker
import app.wispnotes.extract.Extracted
import app.wispnotes.extract.Extractors
import app.wispnotes.extract.OkHttpFetcher
import app.wispnotes.note.Note
import app.wispnotes.note.NoteDraft
import app.wispnotes.note.Source
import app.wispnotes.reader.MetaSettings
import app.wispnotes.reader.ReaderActivity
import app.wispnotes.reader.ReelAction
import app.wispnotes.reader.ReelPolicy
import app.wispnotes.ui.Ghost
import app.wispnotes.ui.WispButton
import app.wispnotes.ui.WispChip
import app.wispnotes.ui.theme.WispColors
import app.wispnotes.ui.theme.WispTheme
import app.wispnotes.vault.SafVaultRepository
import app.wispnotes.vault.SavedLink
import app.wispnotes.vault.VaultRepository
import coil.compose.AsyncImage
import java.net.URI
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
                // The scrim shrinks with the keyboard, so the cap below is the space the sheet really has.
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xD90B0A0F))
                        .imePadding()
                        .clickable(role = Role.Button) { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    SaveSheet(url, title, repo, maxHeight = maxHeight * 0.92f) { finish() }
                }
            }
        }
    }
}

@Composable
private fun SaveSheet(
    url: String,
    initialTitle: String,
    repo: VaultRepository,
    maxHeight: Dp,
    close: () -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var titleEdited by remember { mutableStateOf(initialTitle.isNotBlank()) }
    var caption by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Extracted?>(null) }
    var saving by remember { mutableStateOf(false) }
    var caught by remember { mutableStateOf(false) }
    var needLogin by remember { mutableStateOf(false) }
    /** Set when this link is already in the vault, so the sheet asks before writing a second one. */
    var duplicate by remember { mutableStateOf<SavedLink?>(null) }
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

    // One save path for "Save" and "Save a copy"; only the copy path skips the duplicate check.
    suspend fun save(allowCopy: Boolean) {
        saving = true
        status = null
        duplicate = null
        if (!allowCopy && url.isNotBlank()) {
            // A failed lookup must never block a save, so it counts as "not saved yet".
            val found = runCatching { repo.findInboxNote(url) }.getOrNull()
            if (found != null) {
                duplicate = found
                saving = false
                return
            }
        }
        if (!titleEdited && title.isBlank() && url.isNotBlank()) {
            withTimeoutOrNull(3_000) {
                while (preview?.title == null) delay(40)
            }
        }
        if (!repo.hasAccess()) {
            saving = false
            status = "Open Wisp and pick a vault first."
            return
        }
        val savedTitle = title.trim().ifEmpty { preview?.title ?: fallbackTitle(url) }
        val draft = NoteDraft(info, url, savedTitle, LocalDate.now(), caption)
        repo.writeInboxNote(Note.fileName(draft), Note.render(draft))
            .onSuccess { name ->
                if (url.isNotBlank()) ExtractWorker.enqueue(ctx, name)
                title = savedTitle
                if (info.source == "instagram" && info.type == "reel") {
                    // A reel's page has no readable text, so Meta AI is the only reader.
                    // Off by default: the note is saved and nothing leaves the phone.
                    val action = ReelPolicy.action(
                        MetaSettings.summarise(ctx).first(),
                        MetaSettings.loginState(ctx).first(),
                    )
                    when (action) {
                        ReelAction.ReadNow -> {
                            ctx.startActivity(ReaderActivity.intent(ctx, name))
                            close()
                        }
                        ReelAction.SaveAndWarnLogin -> needLogin = true
                        ReelAction.SaveOnly -> caught = true
                    }
                } else {
                    caught = true
                }
            }
            .onFailure { status = "Could not save: ${it.message}" }
        saving = false
    }

    // A plain val, so the branches below can read the file name without a smart cast.
    val dup = duplicate

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clickable(enabled = false) {}
            .clip(shape)
            .background(WispColors.Purple)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The body grows with the content, then scrolls. The footer below never scrolls, so a
        // 2,000-character caption cannot push Save off the bottom of the screen (#12).
        Column(
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 18.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                dup != null -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ghost(64.dp)
                        androidx.compose.material3.Icon(painterResource(R.drawable.stk_cauldron), null, tint = Color.Unspecified, modifier = Modifier.size(48.dp))
                    }
                    Text("Already saved.", style = MaterialTheme.typography.headlineMedium)
                    Text(dup.title, color = WispColors.Haze, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(dup.fileName, color = WispColors.Lavender, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                needLogin -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ghost(64.dp)
                        androidx.compose.material3.Icon(painterResource(R.drawable.stk_link), null, tint = Color.Unspecified, modifier = Modifier.size(48.dp))
                    }
                    Text("Saved.", style = MaterialTheme.typography.headlineMedium)
                    Text("Log in to Meta AI in Settings to get a summary.", color = WispColors.Haze)
                }
                caught -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ghost(64.dp)
                        androidx.compose.material3.Icon(painterResource(R.drawable.stk_link), null, tint = Color.Unspecified, modifier = Modifier.size(48.dp))
                    }
                    Text("Caught it!", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "\"${title.ifBlank { preview?.title ?: fallbackTitle(url) }}\" is in the pot.",
                        color = WispColors.Haze,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        info.tags.forEach { WispChip("#$it") }
                        preview?.minutes?.let { WispChip("~$it min") }
                    }
                }
                else -> {
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
                            // Four lines, then the field scrolls inside itself: a caption is not a headline.
                            maxLines = 4,
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
                                maxLines = 3,
                                decorationBox = { inner ->
                                    if (caption.isEmpty()) Text("Paste caption", color = WispColors.Lavender)
                                    inner()
                                },
                            )
                        }
                    }
                    status?.let { Text(it, color = WispColors.Haze) }
                }
            }
        }

        // Always visible, always last, and close to the thumb.
        Column(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                dup != null -> {
                    WispButton(
                        "Open the saved note",
                        onClick = { ctx.startActivity(MainActivity.openNote(ctx, dup.fileName)); close() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    WispButton(
                        "Save a copy",
                        onClick = { scope.launch { save(allowCopy = true) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving,
                        color = WispColors.Ink,
                        textColor = Color.White,
                    )
                    WispButton("Cancel", close, Modifier.fillMaxWidth(), color = WispColors.Purple, textColor = Color.White)
                }
                needLogin -> {
                    WispButton(
                        "Log in to Meta AI",
                        onClick = { ctx.startActivity(ReaderActivity.loginIntent(ctx)); close() },
                        modifier = Modifier.fillMaxWidth(),
                        color = WispColors.Pumpkin,
                        textColor = WispColors.Void,
                    )
                    WispButton("Done", close, Modifier.fillMaxWidth(), color = WispColors.Purple, textColor = Color.White)
                }
                caught -> {
                    WispButton("Done", close, Modifier.fillMaxWidth())
                    Text("Closes by itself in 3 s", color = WispColors.Haze, style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    WispButton(
                        label = if (saving) "Saving…" else "Save",
                        onClick = { if (!saving) scope.launch { save(allowCopy = false) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving,
                    )
                }
            }
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
