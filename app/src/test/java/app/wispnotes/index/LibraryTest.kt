package app.wispnotes.index

import app.wispnotes.note.Publish
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTest {
    private fun e(name: String, title: String, body: String = "", tags: String = "", status: String = "done", rating: Int? = null) =
        NoteEntry(name, title, "web", "article", "", status, null, null, tags, "2026-11-01", "done", rating = rating, body = body)

    private val all = listOf(
        e("a", "Haunted houses", "a long read about candles", "article,spooky", rating = 5),
        e("b", "Kotlin tips", "coroutines and flows", "article,code", rating = 3),
        e("c", "Ghost recipes", "Candle wax and pumpkins", "food", status = "queued"),
        e("d", "Old thing", "", "code", status = "archived"),
    )

    private fun names(q: String = "", tag: String? = null, min: Int? = null) =
        NoteSearch.filter(all, q, tag, min).map { it.fileName }

    @Test fun searchesTitleBodyAndTags() {
        assertEquals(listOf("a"), names("haunted"))
        assertEquals(listOf("a", "c"), names("CANDLE"))
        assertEquals(listOf("b", "d"), names("code"))
        assertEquals(listOf("a", "b", "c", "d"), names("  "))
    }

    @Test fun everyWordMustMatch() {
        assertEquals(listOf("c"), names("candle pumpkins"))
        assertEquals(emptyList<String>(), names("candle kotlin"))
    }

    @Test fun filtersByTagAndRating() {
        assertEquals(listOf("b", "d"), names(tag = "CODE"))
        assertEquals(listOf("a"), names(min = 4))
        assertEquals(listOf("a", "b"), names(min = 3)) // unrated notes count as 0
        assertEquals(listOf("b"), names("kotlin", tag = "article", min = 3))
    }

    @Test fun tagsByUse() {
        assertEquals(listOf("article", "code", "food", "spooky"), NoteSearch.tags(all))
    }

    @Test fun groupsByStatusInOrderSkippingEmpty() {
        val g = NoteSearch.groupByStatus(all + e("e", "x", status = "in-progress") + e("f", "y", status = "weird"))
        assertEquals(listOf("in-progress", "queued", "done", "archived", "weird"), g.map { it.first })
        assertEquals(listOf("a", "b"), g[2].second.map { it.fileName })
    }

    @Test fun publishDraftUsesTitleRatingAndNotes() {
        assertEquals("# T\n\n★★★★☆  4/5\n\nmy notes", Publish.draft("T", 4, "  my notes\n"))
        assertEquals("# T\n\n(no notes yet)", Publish.draft("T", null, ""))
    }
}
