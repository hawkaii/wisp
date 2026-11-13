package app.wispnotes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wispnotes.index.Indexer
import app.wispnotes.index.NoteEntry
import app.wispnotes.index.WispDb
import app.wispnotes.note.Frontmatter
import app.wispnotes.note.Recall
import app.wispnotes.note.Review
import app.wispnotes.note.Sections
import app.wispnotes.vault.SafVaultRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Notes tab (all notes) and the Review tab (done notes that are due). */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SafVaultRepository(app)
    private val dao = WispDb.get(app).notes()

    /** Every note, or null until the index first answers. */
    val all: StateFlow<List<NoteEntry>?> = dao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val query = MutableStateFlow("")
    val tag = MutableStateFlow<String?>(null)
    val minRating = MutableStateFlow<Int?>(null)

    fun rescan() = viewModelScope.launch {
        Indexer.rescan({ repo.listInboxNotes() }, { dao.replaceAll(it) })
    }

    /** The user's own text, shown after they tried to recall. Empty when the file is gone. */
    suspend fun myNotes(fileName: String): String =
        repo.editInboxNote(fileName) { it }.getOrNull()?.let(Sections::myNotes).orEmpty()

    /** Sets the next review from the answer. The file is the truth; the index row follows it. */
    fun answer(entry: NoteEntry, recall: Recall) = viewModelScope.launch {
        val plan = Review.after(entry.reviewStep ?: 0, recall, LocalDate.now())
        val values = Review.planValues(plan)
        dao.put(entry.copy(nextReview = values["next_review"], reviewStep = plan.step)) // the card leaves at once
        repo.editInboxNote(entry.fileName) { Frontmatter.set(it, values) }.onFailure { rescan() }
    }
}
