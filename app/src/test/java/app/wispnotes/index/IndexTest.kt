package app.wispnotes.index

import app.wispnotes.extract.Extracted
import app.wispnotes.extract.Merge
import app.wispnotes.note.Note
import app.wispnotes.note.NoteDraft
import app.wispnotes.note.Source
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexTest {
    private fun note(url: String, title: String, day: Int) =
        Note.render(NoteDraft(Source.detect(url), url, title, LocalDate.of(2026, 11, day)))

    /** A vault folder and an index table, both in memory. */
    private class Fake(val files: MutableList<RawNote>) {
        var rows: List<NoteEntry> = emptyList()
        fun rescan() = runBlocking { Indexer.rescan({ files.toList() }, { rows = it }) }
    }

    private val vault = mutableListOf(
        RawNote("2026-11-10-a.md", note("https://youtu.be/a", "A video", 10)),
        RawNote("2026-11-11-b.md", note("https://example.com/b", "An article", 11)),
        RawNote("readme.md", "# Not a Wisp note\n\nno frontmatter"),
        RawNote("other.md", "---\ntitle: Obsidian note\n---\nhello"),
    )

    @Test fun deletingTheIndexAndRescanningRestoresIt() {
        val fake = Fake(vault)
        fake.rescan()
        val first = fake.rows
        assertEquals(listOf("2026-11-10-a.md", "2026-11-11-b.md"), first.map { it.fileName })

        fake.rows = emptyList() // index deleted
        fake.rescan()
        assertEquals(first, fake.rows)
    }

    @Test fun rescanFollowsTheFiles() {
        val fake = Fake(vault)
        fake.rescan()
        fake.files.removeAt(0) // deleted in Obsidian
        val archived = fake.files[0].content.replace("status: queued", "status: archived")
        fake.files[0] = RawNote(fake.files[0].fileName, archived)
        fake.rescan()
        assertEquals(listOf("archived"), fake.rows.map { it.status })
    }

    @Test fun readsFrontmatterIntoARow() {
        val merged = Merge.apply(note("https://youtu.be/a", "youtu.be", 10), Extracted("T", "Chan", "https://i/x.jpg", 4, "d"))
        val e = NoteEntry.parse("n.md", merged)!!
        assertEquals("youtube", e.source)
        assertEquals("T", e.title)
        assertEquals(4, e.estMinutes)
        assertEquals("https://i/x.jpg", e.thumbnail)
        assertEquals("video,youtube", e.tags)
        assertEquals("done", e.extracted)
        assertEquals("2026-11-10", e.saved)
        assertNull(NoteEntry.parse("n.md", "no frontmatter"))
    }

    private fun entry(name: String, minutes: Int?, saved: String = "2026-11-01", status: String = "queued") =
        NoteEntry(name, name, "web", "article", "", status, minutes, null, "", saved, "done")

    @Test fun pendingReturnsOnlyPendingEntries() {
        val pending = entry("pending", null).copy(extracted = "pending")
        val done = entry("done", 5).copy(extracted = "done")
        assertEquals(listOf(pending), Indexer.pending(listOf(pending, done)))
    }


        val q = listOf(entry("short", 5), entry("ten", 10), entry("long", 40), entry("mid", 20), entry("unknown", null), entry("done", 10, status = "archived"))
        assertEquals(listOf("ten", "unknown", "short"), Picker.pick(q, 10).map { it.fileName })
        assertEquals(listOf("mid", "ten", "unknown"), Picker.pick(q, 25).map { it.fileName })
        assertEquals("long", Picker.pick(q, 60).first().fileName)
        assertEquals(3, Picker.pick(q, 60).size)
        assertTrue(Picker.pick(listOf(entry("long", 40)), 10).isEmpty())
    }

    @Test fun equalFitPicksTheOldestFirst() {
        val q = listOf(entry("new", 10, "2026-11-12"), entry("old", 10, "2026-11-02"))
        assertEquals(listOf("old", "new"), Picker.pick(q, 10).map { it.fileName })
    }
}
