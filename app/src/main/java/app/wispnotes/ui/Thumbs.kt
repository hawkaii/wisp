package app.wispnotes.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalContext
import app.wispnotes.index.NoteEntry
import app.wispnotes.vault.SafVaultRepository

/**
 * A note's picture now lives next to the note, so `thumbnail` holds a file name and a card needs
 * the vault's `content://` Uri to draw it. That lookup suspends and walks the SAF tree, so the
 * answers are kept for the life of the process. Notes saved before pictures were kept still hold
 * an http URL, which Coil loads directly.
 */
object Thumbs {
    private val resolved = mutableStateMapOf<String, Any?>()

    /** Call when the vault changes: the same file name then points at a different document. */
    fun clear() = resolved.clear()

    /** Coil model for [entry]'s picture, or null while it resolves or when the note has none. */
    @Composable
    fun model(entry: NoteEntry): Any? {
        val name = entry.thumbnail ?: return null
        if (name.startsWith("http://") || name.startsWith("https://")) return name
        val app = LocalContext.current.applicationContext
        LaunchedEffect(name) {
            if (!resolved.containsKey(name)) resolved[name] = SafVaultRepository(app).noteUri(name)
        }
        return resolved[name]
    }
}
