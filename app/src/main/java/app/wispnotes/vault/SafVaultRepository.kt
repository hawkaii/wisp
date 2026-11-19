package app.wispnotes.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import app.wispnotes.index.RawNote
import app.wispnotes.note.Template
import app.wispnotes.note.TemplateFolders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.store by preferencesDataStore("wisp")
private val KEY_VAULT = stringPreferencesKey("vault_uri")
private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")

/** The folder an older install wrote into. Used once, to keep those notes visible after the upgrade. */
private const val LEGACY_FOLDER = "Inbox"

/** The folder the app keeps template files in. */
private const val TEMPLATE_FOLDER = "templates"

/** Obsidian's own folders: they hold files, not notes the library should show. */
private val SKIP_FOLDERS = setOf("Attachments", "Templates", "attachments", "templates")

// shortcut: a vault is not a filesystem. Stop rather than walk ten thousand files over SAF on every resume.
private const val MAX_DEPTH = 6
private const val MAX_NOTES = 2000

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
            val vault = if (root.name == "Wisp" || root.findFile(".obsidian") != null) root else dir(root, "Wisp")
            val obsidian = dir(vault, ".obsidian")
            if (obsidian.findFile("app.json") == null) {
                val f = obsidian.createFile("application/json", "app.json") ?: error("Cannot create app.json")
                ctx.contentResolver.openOutputStream(f.uri, "wt")?.use { it.write("{}".toByteArray()) }
            }
            dir(vault, TemplateFolders.DEFAULT)
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

    override suspend fun writeNote(fileName: String, content: String, folder: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = vaultRoot() ?: error("No vault chosen")
                val where = clean(folder)
                val target = folderOf(root, where, create = true) ?: error("Cannot open $where")
                val base = fileName.removeSuffix(".md")
                var name = fileName
                var n = 2
                while (target.findFile(name) != null) name = "$base-${n++}.md"
                // octet-stream keeps the ".md" name as is; some providers add ".txt" for text/*.
                val file = target.createFile("application/octet-stream", name) ?: error("Cannot create $name")
                ctx.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(content.toByteArray()) }
                    ?: error("Cannot write $name")
                join(where, name)
            }
        }

    override suspend fun writeFile(fileName: String, mime: String, bytes: ByteArray, folder: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = vaultRoot() ?: error("No vault chosen")
                val target = folderOf(root, clean(folder), create = true) ?: error("No vault chosen")
                // Replace, so a second extraction of the same note does not leave "-2", "-3" copies behind.
                target.findFile(fileName)?.delete()
                val file = target.createFile(mime, fileName) ?: error("Cannot create $fileName")
                ctx.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(bytes) }
                    ?: error("Cannot write $fileName")
                file.name ?: fileName
            }
        }

    // shortcut: reads every file in full over SAF on each scan; fine for hundreds of notes, read only the head if a vault gets huge.
    override suspend fun listNotes(): List<RawNote> = withContext(Dispatchers.IO) {
        val root = vaultRoot() ?: return@withContext emptyList()
        // Only the folders templates name. The rest of the vault is the user's, not the library.
        val folders = TemplateFolders.of(
            readTemplates().mapNotNull { (_, text) -> Template.fromJson(text).getOrNull() },
        )
        val out = ArrayList<RawNote>()
        for (folder in folders) {
            val doc = root.findFile(folder)?.takeIf { it.isDirectory } ?: continue
            walk(doc, "$folder/", out, 0)
            if (out.size >= MAX_NOTES) break
        }
        out
    }

    // The truth is the frontmatter, not the Room index: the index can lag a save by seconds.
    override suspend fun findNote(url: String): SavedLink? =
        withContext(Dispatchers.IO) { Duplicates.match(listNotes(), url) }

    // shortcut: "wt" rewrites in place (SAF has no atomic rename), and an edit made in Obsidian between our read and write is lost; the window is milliseconds.
    override suspend fun editNote(path: String, edit: (String) -> String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = vaultRoot() ?: error("No vault chosen")
                val file = resolve(root, path) ?: error("$path not found")
                val old = ctx.contentResolver.openInputStream(file.uri)?.use { it.readBytes().decodeToString() }
                    ?: error("Cannot read $path")
                val new = edit(old)
                if (new != old) {
                    ctx.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(new.toByteArray()) }
                        ?: error("Cannot write $path")
                }
                new
            }
        }

    override suspend fun noteUri(path: String): Uri? = withContext(Dispatchers.IO) {
        vaultRoot()?.let { resolve(it, path)?.uri }
    }

    /**
     * The JSON files in `Wisp/templates/`. They are real files in the vault, not a hidden string:
     * the user can open one in Obsidian, and an export imports into the clipper.
     */
    override suspend fun readTemplates(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val templates = templatesDir(create = false) ?: return@runCatching emptyList<Pair<String, String>>()
            templates.listFiles()
                .filter { it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true }
                .sortedBy { it.name.orEmpty() }
                .mapNotNull { file ->
                    val text = runCatching {
                        ctx.contentResolver.openInputStream(file.uri)?.use { it.readBytes().decodeToString() }
                    }.getOrNull()
                    if (text == null) null else file.name.orEmpty() to text
                }
        }.getOrDefault(emptyList())
    }

    // A seed, not a fallback: a vault with any template file at all is never written to.
    override suspend fun ensureDefaultTemplate(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val templates = templatesDir(create = true) ?: error("No vault chosen")
            if (templates.listFiles().any { it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true }) return@runCatching
            val name = Template.DEFAULT_FILE
            templates.findFile(name)?.delete()
            val file = templates.createFile("application/json", name) ?: error("Cannot create $name")
            val seed = Template.default().copy(path = seedFolder())
            ctx.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(seed.toJson().toByteArray()) }
                ?: error("Cannot write $name")
        }
    }

    /**
     * Where the seeded template points.
     *
     * An install from before templates wrote into `Inbox/`. Seeding `Clippings/` would hide every one
     * of those notes on the first launch after the upgrade, so the folder follows the notes that are
     * already there. This runs once, when the templates folder is empty, and never again.
     */
    private suspend fun seedFolder(): String {
        val root = runCatching { DocumentFile.fromTreeUri(ctx, vaultUri.first() ?: return TemplateFolders.DEFAULT) }.getOrNull()
            ?: return TemplateFolders.DEFAULT
        val inbox = root.findFile(LEGACY_FOLDER)?.takeIf { it.isDirectory } ?: return TemplateFolders.DEFAULT
        val hasNotes = runCatching { inbox.listFiles().any { it.isFile && it.name?.endsWith(".md", ignoreCase = true) == true } }
            .getOrDefault(false)
        return if (hasNotes) LEGACY_FOLDER else TemplateFolders.DEFAULT
    }

    override suspend fun writeTemplate(fileName: String, text: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = templateFileName(fileName)
                val templates = templatesDir(create = true) ?: error("No vault chosen")
                templates.findFile(name)?.delete()
                val file = templates.createFile("application/json", name) ?: error("Cannot create $name")
                ctx.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(text.toByteArray()) }
                    ?: error("Cannot write $name")
                file.name ?: name
            }
        }

    override suspend fun deleteTemplate(fileName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val name = templateFileName(fileName)
            val templates = templatesDir(create = false) ?: error("No vault chosen")
            val file = templates.findFile(name) ?: error("$name not found")
            require(file.delete()) { "Cannot delete $name" }
        }
    }

    private suspend fun templatesDir(create: Boolean): DocumentFile? {
        val root = vaultRoot() ?: return null
        if (!create) return root.findFile(TEMPLATE_FOLDER)?.takeIf { it.isDirectory }
        return dir(root, TEMPLATE_FOLDER)
    }

    /** A template file is a lowercase slug with `.json`: the name is also the template's id. */
    private fun templateFileName(fileName: String): String {
        require(fileName.endsWith(".json", ignoreCase = true)) { "A template file ends in .json." }
        val slug = fileName.dropLast(5)
            .lowercase()
            .replace(Regex("[^a-z0-9-]+"), "-")
            .trim('-')
        require(slug.isNotEmpty()) { "A template needs a file name." }
        return "$slug.json"
    }

    private suspend fun vaultRoot(): DocumentFile? {
        val uri = vaultUri.first() ?: return null
        return DocumentFile.fromTreeUri(ctx, uri)
    }

    /** Walks the vault. Returns each `.md` with its path relative to the root, e.g. `Clippings/essay.md`. */
    private fun walk(dir: DocumentFile, prefix: String, out: MutableList<RawNote>, depth: Int) {
        if (depth > MAX_DEPTH || out.size >= MAX_NOTES) return
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray<DocumentFile>())
        for (f in children) {
            val name = f.name ?: continue
            if (name.startsWith(".") || name in SKIP_FOLDERS) continue
            if (f.isDirectory) {
                walk(f, "$prefix$name/", out, depth + 1)
            } else if (name.endsWith(".md", ignoreCase = true)) {
                val text = runCatching {
                    ctx.contentResolver.openInputStream(f.uri)?.use { it.readBytes().decodeToString() }
                }.getOrNull() ?: continue
                out += RawNote("$prefix$name", text)
            }
            if (out.size >= MAX_NOTES) return
        }
    }

    /** `Clippings`, `References/Books`, or "" for the root. Creates the folders when [create] is true. */
    private fun folderOf(root: DocumentFile, folder: String, create: Boolean): DocumentFile? {
        var doc: DocumentFile = root
        for (part in clean(folder).split('/').filter { it.isNotBlank() }) {
            doc = doc.findFile(part)?.takeIf { it.isDirectory }
                ?: if (create) dir(doc, part) else return null
        }
        return doc
    }

    /**
     * A path from the root. A bare name is a picture beside the notes, or a note saved before the
     * app stored paths, so it is looked up in the note folder first and in `Inbox/` second.
     */
    private suspend fun resolve(root: DocumentFile, path: String): DocumentFile? {
        val clean = path.trim().trimStart('/')
        if (clean.isBlank()) return null
        if ('/' in clean) {
            return folderOf(root, clean.substringBeforeLast('/'), create = false)
                ?.findFile(clean.substringAfterLast('/'))
        }
        // A bare name is a picture beside its note. It lives in one of the template folders, so ask
        // each of them rather than inventing a default.
        val folders = TemplateFolders.of(readTemplates().mapNotNull { (_, text) -> Template.fromJson(text).getOrNull() })
        for (folder in folders) {
            folderOf(root, folder, create = false)?.findFile(clean)?.let { return it }
        }
        return null
    }

    private fun clean(folder: String) = folder.trim().trim('/')

    private fun join(folder: String, name: String) = if (clean(folder).isBlank()) name else "${clean(folder)}/$name"

    private fun dir(parent: DocumentFile, name: String): DocumentFile =
        parent.findFile(name)?.takeIf { it.isDirectory }
            ?: parent.createDirectory(name)
            ?: error("Cannot create $name")
}
