package app.wispnotes.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
fun SettingsScreen(vaultPath: String, onChangeVault: (Uri) -> Unit, onBack: () -> Unit) {
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
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    Text("Wisp is dark only, on purpose. There is no light theme yet.", color = WispColors.Lavender)
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
