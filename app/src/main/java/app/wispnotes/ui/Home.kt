package app.wispnotes.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.wispnotes.index.NoteEntry
import app.wispnotes.index.Picker
import app.wispnotes.ui.theme.WispCard
import app.wispnotes.ui.theme.WispColors
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Home = the queue: saved notes from the index, filter by source, search, swipe to archive, and "What now?". */
@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val source by vm.source.collectAsStateWithLifecycle()
    var whatNow by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Files can change while we are away (a share, Obsidian): scan again each time Home shows.
    LifecycleResumeEffect(Unit) {
        vm.rescan()
        onPauseOrDispose {}
    }

    // shortcut: hands the .md to whatever app opens it; an in-app note screen comes with the notes milestone.
    val open: (NoteEntry) -> Unit = { entry ->
        scope.launch {
            val uri = vm.uriOf(entry)
            try {
                if (uri == null) throw ActivityNotFoundException()
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/plain")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
                )
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(ctx, "No app can open this note", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val all = queue.orEmpty()
    val shown = remember(all, search, source) {
        all.filter { e ->
            (source == null || e.source == source) &&
                (search.isBlank() || listOf(e.title, e.source, e.tags).any { it.contains(search.trim(), ignoreCase = true) })
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Home", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Text(
                    "What now?",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp),
                    color = WispColors.Void,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(WispColors.Pumpkin)
                        .clickable(enabled = all.isNotEmpty(), role = Role.Button) { whatNow = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            SearchField(search) { vm.search.value = it }
            val sources = all.map { it.source }.distinct().sorted()
            if (sources.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { WispChip("All", selected = source == null) { vm.source.value = null } }
                    items(sources) { s -> WispChip(s, selected = source == s) { vm.source.value = if (source == s) null else s } }
                }
            }
            when {
                queue == null -> Unit
                all.isEmpty() -> Empty("Nothing queued yet.", "Share a link to Wisp from any app. It lands here as a note.")
                shown.isEmpty() -> Empty("No match.", "Try another word, or clear the filter.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    items(shown, key = { it.fileName }) { e ->
                        QueueCard(e, onOpen = { open(e) }, onArchive = { vm.archive(e) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
        WhatNowSheet(visible = whatNow, queue = all, onOpen = { whatNow = false; open(it) }, onClose = { whatNow = false })
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WispColors.Ink).padding(12.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(WispColors.Pumpkin),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text("Search", color = WispColors.Lavender)
                inner()
            },
        )
    }
}

@Composable
private fun Empty(headline: String, body: String) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueCard(entry: NoteEntry, onOpen: () -> Unit, onArchive: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) { onArchive(); true } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(WispColors.Purple).padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Text("Archive", style = MaterialTheme.typography.labelLarge) }
        },
    ) { CardBody(entry, onOpen) }
}

@Composable
private fun CardBody(entry: NoteEntry, onOpen: () -> Unit) {
    WispCard(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen), color = WispColors.Ink) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumb = Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, WispColors.Void, RoundedCornerShape(14.dp))
            if (entry.thumbnail != null) {
                AsyncImage(model = entry.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = thumb)
            } else {
                Box(thumb.background(WispColors.Purple), contentAlignment = Alignment.Center) { Ghost(44.dp, float = false) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    WispChip(entry.source)
                    Text(minutesLabel(entry), color = WispColors.Lavender, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun minutesLabel(e: NoteEntry) = when {
    e.estMinutes != null -> "~${e.estMinutes} min"
    e.extracted == "pending" -> "reading…"
    else -> "? min"
}

/** 10 / 25 / 60 minutes -> up to 3 picks that fit. The best fit is marked and revealed first. */
@Composable
private fun WhatNowSheet(visible: Boolean, queue: List<NoteEntry>, onOpen: (NoteEntry) -> Unit, onClose: () -> Unit) {
    var minutes by remember { mutableIntStateOf(0) }
    val picks = remember(minutes, queue) { if (minutes == 0) emptyList() else Picker.pick(queue, minutes) }
    var revealed by remember { mutableIntStateOf(0) }
    LaunchedEffect(picks) {
        revealed = 0
        repeat(picks.size) {
            delay(180)
            revealed++
        }
    }
    LaunchedEffect(visible) { if (!visible) minutes = 0 }

    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(role = Role.Button, onClick = onClose))
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(WispColors.Purple)
                    .clickable(enabled = false) {}
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("How much time?", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 25, 60).forEach { m -> WispChip("$m min", selected = minutes == m) { minutes = m } }
                }
                if (minutes != 0 && picks.isEmpty()) {
                    Text("Nothing fits $minutes minutes. Try a longer slot.", color = WispColors.Haze)
                }
                picks.forEachIndexed { i, e ->
                    AnimatedVisibility(i < revealed, enter = fadeIn() + scaleIn(initialScale = 0.9f) + slideInVertically { it / 3 }) {
                        PickCard(e, isDefault = i == 0, onOpen = { onOpen(e) })
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun PickCard(entry: NoteEntry, isDefault: Boolean, onOpen: () -> Unit) {
    WispCard(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen), color = if (isDefault) WispColors.Pumpkin else WispColors.Ink) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isDefault) Text("Start here", style = MaterialTheme.typography.labelMedium, color = WispColors.Void)
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDefault) WispColors.Void else Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${entry.source} · ${minutesLabel(entry)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDefault) WispColors.Void else WispColors.Lavender,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}
