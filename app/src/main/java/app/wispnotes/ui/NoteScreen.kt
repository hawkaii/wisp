package app.wispnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.wispnotes.index.NoteEntry
import app.wispnotes.note.Publish
import app.wispnotes.note.Review
import app.wispnotes.note.parseMarkdown
import app.wispnotes.ui.theme.LocalReduceMotion
import app.wispnotes.ui.theme.WispCard
import app.wispnotes.ui.theme.WispColors
import app.wispnotes.ui.theme.WispPanel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** One note: header, editable `## My notes`, status buttons, the Finish flow, then the Markdown body. */
@Composable
fun NoteScreen(fileName: String, shared: Shared?, onBack: () -> Unit, vm: NoteViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var finishing by rememberSaveable(fileName) { mutableStateOf(false) }
    var rating by rememberSaveable(fileName) { mutableIntStateOf(0) }
    var publish by rememberSaveable(fileName) { mutableStateOf(false) }

    LaunchedEffect(fileName) { vm.open(fileName) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }
    val back = { vm.flush(); onBack() }
    BackHandler(onBack = back)

    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.size(48.dp).pressable(onClick = back).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
        }
        val note = ui?.takeIf { it.fileName == fileName }
        when {
            note != null -> {
                val e = note.entry
                val blocks = remember(note.body) { parseMarkdown(note.body) }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Header(e, note.author, shared) }
                    item {
                        Actions(
                            e = e,
                            canFinish = Review.hasEnoughNotes(notes),
                            finishing = finishing,
                            onStart = vm::start,
                            onOpen = { uriHandler.openUri(e.url) },
                            onFinish = { finishing = true },
                            onPublish = { publish = !publish },
                        )
                    }
                    item {
                        AnimatedVisibility(finishing && e.status == "in-progress", enter = fadeIn() + slideInVertically { it / 4 }) {
                            FinishCard(
                                rating = rating,
                                onRate = { rating = it },
                                onCancel = { finishing = false },
                                onDone = { vm.finish(rating); finishing = false },
                            )
                        }
                    }
                    item {
                        AnimatedVisibility(publish && e.status == "done", enter = fadeIn() + scaleIn(initialScale = 0.92f)) {
                            PublishPreview(Publish.draft(e.title, e.rating, notes))
                        }
                    }
                    item { MyNotes(notes, vm::onNotes) }
                    items(blocks) { MdLineView(it) }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
            missing -> Message("Note not found.", "It may have moved or been deleted in your vault.")
            else -> Unit
        }
    }
}

@Composable
private fun Message(headline: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Ghost(96.dp)
        Text(headline, style = MaterialTheme.typography.titleMedium)
        Text(body, color = WispColors.Lavender)
    }
}

@Composable
private fun Header(e: NoteEntry, author: String?, shared: Shared?) {
    WispCard(Modifier.noteBounds(shared, e.fileName).fillMaxWidth(), color = WispColors.Ink) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val thumb = Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, WispColors.Void, RoundedCornerShape(14.dp))
                if (e.thumbnail != null) {
                    AsyncImage(model = e.thumbnail, contentDescription = "Thumbnail", contentScale = ContentScale.Crop, modifier = thumb)
                } else {
                    Box(thumb.background(WispColors.Purple), contentAlignment = Alignment.Center) { Ghost(44.dp, float = false) }
                }
                Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                WispChip(statusLabel(e.status), selected = e.status != "queued")
            }
            val meta = listOfNotNull(
                e.estMinutes?.let { "~$it min" },
                author,
                e.saved.takeIf { it.isNotEmpty() }?.let { "saved $it" },
                e.rating?.let { Publish.stars(it) },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, color = WispColors.Lavender, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

internal fun statusLabel(s: String) = when (s) {
    "queued" -> "Queued"
    "in-progress" -> "In progress"
    "done" -> "Done"
    "archived" -> "Archived"
    else -> s
}

@Composable
private fun Actions(
    e: NoteEntry,
    canFinish: Boolean,
    finishing: Boolean,
    onStart: () -> Unit,
    onOpen: () -> Unit,
    onFinish: () -> Unit,
    onPublish: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (e.status) {
            "in-progress" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                WispButton("Open", onOpen, Modifier.weight(1f), color = WispColors.Purple, textColor = Color.White, compact = true)
                WispButton("Finish", onFinish, Modifier.weight(1f), enabled = canFinish && !finishing, compact = true)
            }
                if (!canFinish) Text("Write 2 lines in My notes first. Then you can finish and rate it.", color = WispColors.Lavender)
            }
            "done" -> {
                WispButton("Open", onOpen, color = WispColors.Purple, textColor = Color.White)
                WispButton("Publish (mock)", onPublish)
                Text(
                    e.nextReview?.let { "Comes back for review on $it." } ?: "Comes back for review soon.",
                    color = WispColors.Lavender,
                )
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WispButton("Start", { onStart(); onOpen() }, Modifier.weight(1f), compact = true)
                    WispButton("Open", onOpen, Modifier.weight(1f), color = WispColors.Purple, textColor = Color.White, compact = true)
                }
            }
        }
    }
}

@Composable
private fun FinishCard(rating: Int, onRate: (Int) -> Unit, onCancel: () -> Unit, onDone: () -> Unit) {
    WispCard(Modifier.fillMaxWidth(), color = WispColors.Purple) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("How was it?", style = MaterialTheme.typography.titleMedium)
            GhostRating(rating, onRate)
            Text("$rating of 5", color = WispColors.Haze, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                WispButton("Done", onDone, enabled = rating > 0, compact = true)
                WispChip("Not yet", onClick = onCancel)
            }
        }
    }
}

@Composable
private fun MyNotes(value: String, onChange: (String) -> Unit) {
    WispCard(Modifier.fillMaxWidth(), color = WispColors.Purple) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("YOUR 2–3 LINES", style = MaterialTheme.typography.labelMedium, color = WispColors.Haze)
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WispColors.Ink).padding(12.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp, lineHeight = 22.sp),
                cursorBrush = SolidColor(WispColors.Pumpkin),
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).semantics { contentDescription = "My notes" },
                decorationBox = { inner ->
                    if (value.isEmpty()) Text("What do you think? Two lines is enough.", color = WispColors.Lavender)
                    inner()
                },
            }
        }
    }
}
}

/** Typewriter preview of the blog draft. Labelled as a preview: nothing is sent anywhere. */
@Composable
private fun PublishPreview(draft: String) {
    val reduce = LocalReduceMotion.current
    var shown by remember(draft) { mutableIntStateOf(if (reduce) draft.length else 0) }
    LaunchedEffect(draft) {
        while (shown < draft.length) {
            delay(16)
            shown = (shown + 2).coerceAtMost(draft.length)
        }
    }
    WispCard(Modifier.fillMaxWidth(), color = WispColors.Ink) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WispChip("Preview only · nothing is published", selected = true)
            parseMarkdown(draft.take(shown)).forEach { MdLineView(it) }
        }
    }
}
