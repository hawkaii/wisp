package app.wispnotes.ui

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.wispnotes.R
import app.wispnotes.index.NoteEntry
import app.wispnotes.index.Picker
import app.wispnotes.ui.theme.WispCard
import app.wispnotes.ui.theme.WispColors
import app.wispnotes.ui.theme.WispPanel

/** Home is the queue: a pot, one main action, and the newest notes. */
@Composable
fun HomeScreen(
    shared: Shared?,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onWhatNow: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.rescan()
        onPauseOrDispose {}
    }
    val all = queue.orEmpty()

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Home", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Box(Modifier.size(48.dp).pressable(onClick = onSettings).semantics { contentDescription = "Settings" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Settings, null, tint = WispColors.Lavender)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.stk_moon), null, tint = Color.Unspecified, modifier = Modifier.size(26.dp))
            Text("Good evening", style = MaterialTheme.typography.bodySmall, color = WispColors.Lavender)
        }
        Text("Your pot", style = MaterialTheme.typography.headlineMedium)
        PotCard(all)
        WispButton("What now?", onWhatNow, Modifier.height(64.dp))
        if (all.isEmpty()) {
            Empty("Nothing queued yet.", "Share a link to Wisp from any app. It lands here as a note.")
        } else {
            WispCard(Modifier.fillMaxWidth(), color = WispColors.Ink) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    all.take(3).forEach { entry ->
                        QueueRow(entry, onOpen = { onOpen(entry.fileName) }, onRetry = { vm.retry(entry) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PotCard(entries: List<NoteEntry>) {
    val minutes = entries.sumOf { it.estMinutes ?: 0 }
    WispCard(Modifier.fillMaxWidth(), color = WispColors.Purple) {
        Box(Modifier.fillMaxWidth().padding(12.dp)) {
            Icon(painterResource(R.drawable.stk_bat), null, tint = Color.Unspecified, modifier = Modifier.size(44.dp).align(Alignment.TopStart))
            WispPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.stk_cauldron), null, tint = Color.Unspecified, modifier = Modifier.fillMaxSize())
                        Text(entries.size.toString(), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                    Text("${entries.size} brewing", style = MaterialTheme.typography.titleMedium)
                    Text(totalMinutes(minutes), color = WispColors.Lavender, style = MaterialTheme.typography.bodySmall)
                }
            }
            Icon(painterResource(R.drawable.stk_pumpkin), null, tint = Color.Unspecified, modifier = Modifier.size(54.dp).align(Alignment.BottomEnd))
        }
    }
}

private fun totalMinutes(minutes: Int): String = "about ${minutes / 60} h ${minutes % 60} min"

@Composable
private fun QueueRow(entry: NoteEntry, onOpen: () -> Unit, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onOpen).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(WispColors.Pumpkin).border(2.dp, Color.Black, RoundedCornerShape(50)))
        Text(entry.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(minutesLabel(entry), color = WispColors.Lavender, style = MaterialTheme.typography.bodySmall)
        if (entry.extracted == "pending") WispButton("Retry", onRetry, compact = true)
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WispColors.Ink).padding(12.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(WispColors.Pumpkin),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search" },
            decorationBox = { inner ->
                if (value.isEmpty()) Text("Search", color = WispColors.Lavender)
                inner()
            },
        )
    }
}

@Composable
fun Empty(headline: String, body: String) {
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

@Composable
fun WhatNowScreen(onBack: () -> Unit, vm: HomeViewModel = viewModel()) {
    BackHandler(onBack = onBack)
    val queue by vm.queue.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var minutes by remember { mutableIntStateOf(25) }
    val picks = remember(minutes, queue) { Picker.pick(queue.orEmpty(), minutes) }
    val selected = picks.firstOrNull()
    LifecycleResumeEffect(Unit) {
        vm.rescan()
        onPauseOrDispose {}
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).pressable(onClick = onBack), contentAlignment = Alignment.Center) { Text("‹", fontSize = 36.sp) }
            Text("How long do you have?", style = MaterialTheme.typography.headlineMedium)
        }
        WispCard(Modifier.fillMaxWidth(), color = WispColors.Purple) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WispPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.stk_gauge), null, tint = Color.Unspecified, modifier = Modifier.fillMaxWidth().height(111.dp))
                        Text("$minutes min", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 25, 60).forEach { value ->
                        WispChip("$value", selected = minutes == value, choice = true, modifier = Modifier.weight(1f)) { minutes = value }
                    }
                }
            }
        }
        selected?.let { PickCard(it, onOpen = { uriHandler.openUri(it.url) }) }
        if (picks.size > 1) {
            WispCard(Modifier.fillMaxWidth(), color = WispColors.Ink) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    picks.drop(1).forEach { entry -> QueueRow(entry, onOpen = { uriHandler.openUri(entry.url) }, onRetry = {}) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (selected != null) {
            WispButton("Start · ${selected.estMinutes ?: minutes} min", {
                vm.start(selected)
                uriHandler.openUri(selected.url)
            })
        }
    }
}

@Composable
private fun PickCard(entry: NoteEntry, onOpen: () -> Unit) {
    WispCard(Modifier.fillMaxWidth().pressable(onClick = onOpen), color = WispColors.Purple) {
        Box(Modifier.fillMaxWidth().border(3.dp, WispColors.Pumpkin, RoundedCornerShape(16.dp)).padding(14.dp)) {
            Text("Wisp's pick", style = MaterialTheme.typography.labelMedium, color = WispColors.Void, modifier = Modifier.background(WispColors.Pumpkin, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))
            Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${entry.source} · ${minutesLabel(entry)}", color = WispColors.Haze, style = MaterialTheme.typography.bodySmall)
            }
            Icon(painterResource(R.drawable.stk_ghost), null, tint = Color.Unspecified, modifier = Modifier.size(46.dp).align(Alignment.TopEnd))
        }
    }
}

private fun minutesLabel(e: NoteEntry) = when {
    e.estMinutes != null -> "~${e.estMinutes} min"
    e.extracted == "pending" -> "reading…"
    else -> "? min"
}
