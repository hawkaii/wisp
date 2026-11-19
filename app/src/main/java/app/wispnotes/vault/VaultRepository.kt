package app.wispnotes.vault

import android.net.Uri
import app.wispnotes.index.RawNote
import kotlinx.coroutines.flow.Flow

/** Everything the app needs from the user's vault folder. Only this file's implementation knows about SAF. */
interface VaultRepository {
    /** Persisted vault root, or null when none was chosen. */
    val vaultUri: Flow<Uri?>

    suspend fun isOnboarded(): Boolean
    suspend fun setOnboarded()

    /** Stores [treeUri] and keeps read+write permission across restarts. */
    suspend fun setVault(treeUri: Uri)

    /** Creates `Wisp/.obsidian/app.json` and the note folder inside [parent], then makes it the vault. */
    suspend fun createVault(parent: Uri): Result<Uri>

    /** False when no vault is set or the persisted permission is gone. */
    suspend fun hasAccess(): Boolean

    /**
     * Where new notes go, relative to the vault root: `"Inbox"` unless the user picked another,
     * `""` for the root itself. Reads follow the same value, so the two never disagree.
     */
    suspend fun noteFolder(): String

    suspend fun setNoteFolder(folder: String)

    /**
     * Writes [content] as a new note in the note folder. Never overwrites: a taken name gets `-2`, `-3`...
     * Returns the note's path relative to the vault root, which is how every other call names it.
     */
    suspend fun writeNote(fileName: String, content: String): Result<String>

    /**
     * The first note that already holds [url], or null.
     * Compares cleaned URLs, so `?igsh=` and a different save date still count as the same link.
     */
    suspend fun findNote(url: String): SavedLink?

    /**
     * Writes [bytes] next to the notes, replacing any file of that name.
     * Used for pictures the note points at, because a remote link can expire. Returns the name written.
     */
    suspend fun writeFile(fileName: String, mime: String, bytes: ByteArray): Result<String>

    /**
     * Every `.md` file in the vault with its text, wherever it sits.
     * `Text/` and other Obsidian folders are walked past; notes in the root and in `Inbox/`,
     * `Clippings/`, `References/` are all included.
     */
    suspend fun listNotes(): List<RawNote>

    /** Reads the note at [path], applies [edit], writes it back only if it changed. Returns the new text. */
    suspend fun editNote(path: String, edit: (String) -> String): Result<String>

    /**
     * Document Uri of [path], for opening it in another app.
     * A bare file name resolves inside the note folder, which is where pictures live.
     */
    suspend fun noteUri(path: String): Uri?
    /**
     * The JSON files in `Wisp/templates/`, as file name to text. Seeds the built-in templates on
     * first use. Empty when there is no vault: callers fall back to
     * [app.wispnotes.note.Templates.builtIn].
     */
    suspend fun readTemplates(): List<Pair<String, String>>
}
