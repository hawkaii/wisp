package app.wispnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.wispnotes.index.NoteEntry
import app.wispnotes.index.NoteSearch
import app.wispnotes.note.Publish
import app.wispnotes.ui.theme.WispCard
import app.wispnotes.ui.theme.WispColors
import coil.compose.AsyncImage

/** Notes = the library: every note grouped by status, with search, tag chips and a rating filter. */
@Composable
fun NotesScreen(onOpen: (String) -> Unit, vm: LibraryViewModel = viewModel()) {
    val all by vm.all.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val tag by vm.tag.collectAsStateWithLifecycle()
    val min by vm.minRating.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.rescan()
        onPauseOrDispose {}
    }
    val notes = all.orEmpty()
    val shown = remember(notes, query, tag, min) { NoteSearch.filter(notes, query, tag, min) }
    val groups = remember(shown) { NoteSearch.groupByStatus(shown) }
    val tags = remember(notes) { NoteSearch.tags(notes) }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Notes", style = MaterialTheme.typography.headlineMedium)
        SearchField(query) { vm.query.value = it }
        if (tags.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { WispChip("All tags", selected = tag == null) { vm.tag.value = null } }
                items(tags) { t -> WispChip(t, selected = tag == t) { vm.tag.value = if (tag == t) null else t } }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { WispChip("Any rating", selected = min == null) { vm.minRating.value = null } }
            items(listOf(3, 4, 5)) { r ->
                WispChip(if (r == 5) "★5" else "★$r+", selected = min == r) { vm.minRating.value = if (min == r) null else r }
            }
        }
        when {
            all == null -> Unit
            notes.isEmpty() -> Empty("No notes yet.", "Every note is one Markdown file in your vault. Obsidian opens them too.")
            shown.isEmpty() -> Empty("No match.", "Try another word, or clear a filter.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                groups.forEach { (status, list) ->
                    item(key = "h-$status") {
                        Text("${statusLabel(status)} · ${list.size}", style = MaterialTheme.typography.labelLarge, color = WispColors.Lavender)
                    }
                    itemsIndexed(list, key = { _, e -> e.fileName }) { i, e ->
                        NoteRow(e, onOpen = { onOpen(e.fileName) }, modifier = Modifier.animateItem().staggerIn(i))
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(e: NoteEntry, onOpen: () -> Unit, modifier: Modifier) {
    WispCard(modifier.fillMaxWidth().pressable(onClick = onOpen), color = WispColors.Ink) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumb = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
            if (e.thumbnail != null) {
                AsyncImage(e.thumbnail, "Thumbnail", contentScale = ContentScale.Crop, modifier = thumb)
            } else {
                Box(thumb.background(WispColors.Purple), contentAlignment = Alignment.Center) { Ghost(34.dp, float = false) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    WispChip(e.source)
                    e.estMinutes?.let { Text("~$it min", color = WispColors.Lavender, style = MaterialTheme.typography.bodySmall) }
                    e.rating?.let { Text(Publish.stars(it), color = WispColors.Pumpkin) }
                }
            }
        }
    }
}
