package app.wispnotes.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wispnotes.extract.ExtractWorker
import app.wispnotes.index.Indexer
import app.wispnotes.index.NoteEntry
import app.wispnotes.index.WispDb
import app.wispnotes.note.Frontmatter
import app.wispnotes.vault.SafVaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SafVaultRepository(app)
    private val dao = WispDb.get(app).notes()

    /** The queue, or null until the index first answers. */
    val queue: StateFlow<List<NoteEntry>?> =
        dao.queue().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val search = MutableStateFlow("")
    val source = MutableStateFlow<String?>(null)

    /** Rebuilds the index from the vault files. Safe to run any time; the files are the truth. */
    fun rescan() = viewModelScope.launch {
        Indexer.rescan({ repo.listInboxNotes() }) { entries ->
            dao.replaceAll(entries)
            Indexer.pending(entries).forEach { ExtractWorker.enqueue(getApplication(), it.fileName) }
        }
    }

    fun retry(entry: NoteEntry) {
        ExtractWorker.enqueue(getApplication(), entry.fileName)
    }

    fun archive(entry: NoteEntry) = viewModelScope.launch {
        dao.put(entry.copy(status = "archived")) // the card leaves the list at once
        repo.editInboxNote(entry.fileName) { Frontmatter.set(it, mapOf("status" to "archived")) }
            .onFailure { rescan() } // file untouched: the next scan brings the card back
    }

    suspend fun uriOf(entry: NoteEntry): Uri? = repo.inboxNoteUri(entry.fileName)
}
