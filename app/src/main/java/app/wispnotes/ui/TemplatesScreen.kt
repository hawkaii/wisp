package app.wispnotes.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.wispnotes.note.Template
import app.wispnotes.ui.theme.WispCard
import app.wispnotes.ui.theme.WispColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Templates. A list of the JSON files in `<vault>/templates/`.
 *
 * Files are the truth, so every action reloads the folder. A file that does not parse is still shown,
 * because the fix is to open it and edit the text.
 */
@Composable
fun TemplatesScreen(
    onOpen: (String?) -> Unit,
    onBack: () -> Unit,
    vm: TemplatesViewModel = viewModel(),
) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<TemplateRow?>(null) }

    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose {}
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            }
            if (text == null) vm.message.value = "Could not read that file."
            else vm.import(text)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).pressable(onClick = onBack).semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Text("Templates", style = MaterialTheme.typography.headlineMedium)
        }

        WispCard(Modifier.fillMaxWidth(), color = WispColors.Ink) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "A template decides the note: its name, its folder, its frontmatter and its text. " +
                        "The format is Obsidian Web Clipper JSON, so a file made there imports here unchanged.",
                    color = WispColors.Lavender,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WispButton("New template", { onOpen(null) }, Modifier.weight(1f))
                    WispButton(
                        label = "Import JSON",
                        onClick = { pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        modifier = Modifier.weight(1f),
                        color = WispColors.Purple,
                        textColor = Color.White,
                    )
                }
            }
        }

        message?.let {
            Text(it, color = WispColors.Pumpkin, style = MaterialTheme.typography.bodySmall)
        }

        when {
            rows == null -> Unit
            rows.orEmpty().isEmpty() -> Empty(
                "No templates yet.",
                "Make one, or import a JSON file from the Obsidian Web Clipper.",
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows.orEmpty(), key = { it.fileName }) { row ->
                    TemplateRowCard(
                        row = row,
                        onOpen = { onOpen(row.fileName) },
                        onDelete = { deleting = row },
                    )
                }
            }
        }
    }

    deleting?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = WispColors.Ink,
            title = { Text("Delete ${row.name}?") },
            text = { Text("The file is removed from your vault. Notes it already made stay.", color = WispColors.Lavender) },
            confirmButton = { TextButton(onClick = { vm.delete(row.fileName); deleting = null }) { Text("Delete", color = WispColors.Pumpkin) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TemplateRowCard(row: TemplateRow, onOpen: () -> Unit, onDelete: () -> Unit) {
    WispCard(Modifier.fillMaxWidth().pressable(onClick = onOpen), color = WispColors.Ink) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (row.isDefault) WispChip("Default")
            }
            row.template?.let { template ->
                Text(
                    "${template.folderLabel()} · ${template.triggerCount()}",
                    color = WispColors.Lavender,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            row.error?.let {
                Text(it, color = WispColors.Pumpkin, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.fileName, color = WispColors.Lavender, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                WispButton("Delete", onDelete, compact = true, color = WispColors.Purple, textColor = Color.White)
            }
        }
    }
}

private fun Template.folderLabel(): String = "Folder $path"

private fun Template.triggerCount(): String =
    if (triggers.isEmpty()) "no trigger" else if (triggers.size == 1) "1 trigger" else "${triggers.size} triggers"
