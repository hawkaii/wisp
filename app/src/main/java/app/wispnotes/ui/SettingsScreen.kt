package app.wispnotes.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.wispnotes.BuildConfig
import app.wispnotes.ui.theme.WispCard
import app.wispnotes.ui.theme.WispColors

private const val SYNC_DOCS = "https://obsidian.md/help/sync-notes"

/** Settings: vault, theme note, a "How to sync" page, version. */
@Composable
fun SettingsScreen(
    vaultPath: String,
    noteFolder: String,
    summariseReels: Boolean,
    metaLoggedIn: Boolean?,
    onChangeVault: (Uri) -> Unit,
    onFolder: (String) -> Unit,
    onSummarise: (Boolean) -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit,
) {
    var sync by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) onChangeVault(uri) }
    val back = { if (sync) sync = false else onBack() }
    BackHandler(onBack = back)

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.size(48.dp).pressable(onClick = back).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (sync) {
                Text("How to sync", style = MaterialTheme.typography.headlineMedium)
                Card {
                    Text("Wisp has no sync of its own. Your notes are plain Markdown files in your vault folder.", style = MaterialTheme.typography.bodyLarge)
                    Text("Sync that folder with any tool: Obsidian Sync, Syncthing, or a cloud folder. Then open the same vault in Obsidian on your computer.", style = MaterialTheme.typography.bodyLarge)
                    WispButton("Open Obsidian sync docs", {
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SYNC_DOCS)))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(ctx, "No browser found", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            } else {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                Card {
                    Text("Vault", style = MaterialTheme.typography.titleMedium)
                    Text(vaultPath.ifEmpty { "No vault chosen" }, color = WispColors.Lavender)
                    WispButton("Change vault", { pick.launch(null) })
                }
                Card {
                    Text("Where new notes go", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Everything in the vault is readable, wherever it sits. This only picks the folder Wisp writes to.",
                        color = WispColors.Lavender,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { WispChip("Inbox", selected = noteFolder == "Inbox") { onFolder("Inbox") } }
                        item { WispChip("Clippings", selected = noteFolder == "Clippings") { onFolder("Clippings") } }
                        item { WispChip("References", selected = noteFolder == "References") { onFolder("References") } }
                        item { WispChip("Root", selected = noteFolder.isBlank()) { onFolder("") } }
                    }
                }
                Card {
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    Text("Wisp is dark only, on purpose. There is no light theme yet.", color = WispColors.Lavender)
                }
                Card {
                    Text("Meta AI", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Summarise reels", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when (metaLoggedIn) {
                                    true -> "Signed in on this phone."
                                    false -> "Not signed in on this phone."
                                    null -> "Not checked on this phone yet."
                                },
                                color = WispColors.Lavender,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = summariseReels,
                            onCheckedChange = onSummarise,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = WispColors.Void,
                                checkedTrackColor = WispColors.Pumpkin,
                                uncheckedThumbColor = WispColors.Lavender,
                                uncheckedTrackColor = WispColors.Void,
                            ),
                        )
                    }
                    Text(
                        "Off by default: a shared reel is only saved. When this is on, the reel is read in a browser inside Wisp and the answer goes into the note.",
                        color = WispColors.Lavender,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (summariseReels && metaLoggedIn == false) {
                        Text("Meta AI needs one sign-in on this phone before it will read a reel.", color = WispColors.Haze)
                    }
                    WispButton(
                        if (metaLoggedIn == true) "Log in again" else "Log in to Meta AI",
                        onLogin,
                        color = WispColors.Purple,
                        textColor = Color.White,
                    )
                    Text("The session stays on this phone. Clearing Wisp's data signs you out.", color = WispColors.Lavender, style = MaterialTheme.typography.bodySmall)
                }
                Card {
                    Text("Sync", style = MaterialTheme.typography.titleMedium)
                    WispButton("How to sync", { sync = true }, color = WispColors.Purple, textColor = Color.White)
                }
                Card {
                    Text("About", style = MaterialTheme.typography.titleMedium)
                    Text("Wisp ${BuildConfig.VERSION_NAME}", color = WispColors.Lavender)
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    WispCard(Modifier.fillMaxWidth(), color = WispColors.Ink) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}
