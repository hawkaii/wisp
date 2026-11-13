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

    /** Creates `Wisp/.obsidian/app.json` and `Wisp/Inbox/` inside [parent], then makes it the vault. */
    suspend fun createVault(parent: Uri): Result<Uri>

    /** False when no vault is set or the persisted permission is gone. */
    suspend fun hasAccess(): Boolean

    /** Writes [content] as a new file in `Inbox/`. Never overwrites: a taken name gets `-2`, `-3`... Returns the final file name. */
    suspend fun writeInboxNote(fileName: String, content: String): Result<String>

    /** Every `.md` file in `Inbox/` with its text. Empty when there is no vault. */
    suspend fun listInboxNotes(): List<RawNote>

    /** Reads `Inbox/[fileName]`, applies [edit], writes it back only if it changed. Returns the new text. */
    suspend fun editInboxNote(fileName: String, edit: (String) -> String): Result<String>

    /** Document Uri of `Inbox/[fileName]`, for opening it in another app. */
    suspend fun inboxNoteUri(fileName: String): Uri?
}
