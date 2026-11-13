package app.wispnotes.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.store by preferencesDataStore("wisp")
private val KEY_VAULT = stringPreferencesKey("vault_uri")
private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")

class SafVaultRepository(context: Context) : VaultRepository {
    private val ctx = context.applicationContext
    private val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    override val vaultUri: Flow<Uri?> = ctx.store.data.map { it[KEY_VAULT]?.let(Uri::parse) }

    override suspend fun isOnboarded() = ctx.store.data.first()[KEY_ONBOARDED] == true

    override suspend fun setOnboarded() {
        ctx.store.edit { it[KEY_ONBOARDED] = true }
    }

    override suspend fun setVault(treeUri: Uri) {
        ctx.contentResolver.takePersistableUriPermission(treeUri, rw)
        ctx.store.edit { it[KEY_VAULT] = treeUri.toString() }
    }

    override suspend fun createVault(parent: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(parent, rw)
            val root = DocumentFile.fromTreeUri(ctx, parent) ?: error("Folder not readable")
            val vault = dir(root, "Wisp")
            val obsidian = dir(vault, ".obsidian")
            if (obsidian.findFile("app.json") == null) {
                val f = obsidian.createFile("application/json", "app.json") ?: error("Cannot create app.json")
                ctx.contentResolver.openOutputStream(f.uri, "wt")?.use { it.write("{}".toByteArray()) }
            }
            dir(vault, "Inbox")
            // The new folder sits under the picked tree, which already carries the persisted grant.
            setVault(vault.uri)
            vault.uri
        }
    }

    override suspend fun hasAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = vaultUri.first() ?: return@withContext false
            val doc = DocumentFile.fromTreeUri(ctx, uri)
            doc != null && doc.exists() && doc.canWrite()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun writeInboxNote(fileName: String, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = vaultUri.first() ?: error("No vault chosen")
                val root = DocumentFile.fromTreeUri(ctx, uri) ?: error("Vault not readable")
                val inbox = dir(root, "Inbox")
                val base = fileName.removeSuffix(".md")
                var name = fileName
                var n = 2
                while (inbox.findFile(name) != null) name = "$base-${n++}.md"
                // octet-stream keeps the ".md" name as is; some providers add ".txt" for text/*.
                val file = inbox.createFile("application/octet-stream", name) ?: error("Cannot create $name")
                ctx.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(content.toByteArray()) }
                    ?: error("Cannot write $name")
            }
        }

    private fun dir(parent: DocumentFile, name: String): DocumentFile =
        parent.findFile(name)?.takeIf { it.isDirectory }
            ?: parent.createDirectory(name)
            ?: error("Cannot create $name")
}
