package app.wispnotes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wispnotes.index.NoteEntry
import app.wispnotes.index.WispDb
import app.wispnotes.note.Frontmatter
import app.wispnotes.note.Review
import app.wispnotes.note.Sections
import app.wispnotes.vault.SafVaultRepository
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class NoteUi(val fileName: String, val entry: NoteEntry, val author: String?, val body: String)

/** The open note: loads the file, autosaves `## My notes` (debounced), moves status, finishes with a rating. */
class NoteViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SafVaultRepository(app)
    private val dao = WispDb.get(app).notes()
    private val lock = Mutex() // one file write at a time, in order
    private var saveJob: Job? = null

    /** Kept after the screen closes so the exit transition still has content; the next [open] replaces it. */
    val ui = MutableStateFlow<NoteUi?>(null)
    val notes = MutableStateFlow("")
    val missing = MutableStateFlow(false)

    fun open(fileName: String) = viewModelScope.launch {
        if (ui.value?.fileName != fileName) ui.value = null
        missing.value = false
        lock.withLock {
            repo.editInboxNote(fileName) { it }
                .onSuccess { show(fileName, it, resetNotes = true) }
                .onFailure { missing.value = true }
        }
    }

    private suspend fun show(fileName: String, content: String, resetNotes: Boolean) {
        val entry = NoteEntry.parse(fileName, content)
        if (entry == null) { missing.value = true; return }
        if (resetNotes) notes.value = Sections.myNotes(content)
        ui.value = NoteUi(fileName, entry, Frontmatter.text(content, "author"), Sections.readable(content))
        dao.put(entry) // lists update at once
    }

    fun onNotes(text: String) {
        notes.value = text
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            write(emptyMap())
        }
    }

    /** Saves right now. Called when leaving the note or the app. */
    fun flush() {
        saveJob?.cancel()
        viewModelScope.launch { write(emptyMap()) }
    }

    fun start() = change(mapOf("status" to "in-progress"))

    fun finish(rating: Int) = change(Review.finishValues(rating, LocalDate.now()))

    private fun change(values: Map<String, String>) {
        saveJob?.cancel()
        viewModelScope.launch { write(values) }
    }

    /** Writes the user's notes and [values] together. Never cancelled half way: a cut write would damage the file. */
    private suspend fun write(values: Map<String, String>) {
        val file = ui.value?.fileName ?: return
        val text = notes.value
        withContext(NonCancellable) {
            lock.withLock {
                repo.editInboxNote(file) { Frontmatter.set(Sections.withMyNotes(it, text), values) }
                    .onSuccess { show(file, it, resetNotes = false) }
            }
        }
    }
}
