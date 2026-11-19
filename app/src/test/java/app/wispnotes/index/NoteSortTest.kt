package app.wispnotes.index

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteSortTest {
    private fun note(file: String, title: String, saved: String, rating: Int? = null) =
        NoteEntry(
            fileName = file,
            title = title,
            source = "instagram",
            type = "reel",
            url = "",
            status = "queued",
            estMinutes = null,
            thumbnail = null,
            tags = "",
            saved = saved,
            extracted = "done",
            rating = rating,
        )

    private val notes = listOf(
        note("b.md", "Banana", "2026-11-10", rating = 3),
        note("a.md", "apple", "2026-11-12", rating = null),
        note("c.md", "Cherry", "2026-11-01", rating = 5),
    )

    @Test
    fun `latest is newest first`() {
        assertEquals(listOf("a.md", "b.md", "c.md"), NoteSort.apply(notes, Sort.Latest).map { it.fileName })
    }

    @Test
    fun `oldest is the reverse`() {
        assertEquals(listOf("c.md", "b.md", "a.md"), NoteSort.apply(notes, Sort.Oldest).map { it.fileName })
    }

    @Test
    fun `title ignores case`() {
        assertEquals(listOf("a.md", "b.md", "c.md"), NoteSort.apply(notes, Sort.Title).map { it.fileName })
    }

    @Test
    fun `rating puts the best first and the unrated last`() {
        assertEquals(listOf("c.md", "b.md", "a.md"), NoteSort.apply(notes, Sort.Rating).map { it.fileName })
    }

    @Test
    fun `a note with no date sorts last under latest`() {
        val withBlank = notes + note("z.md", "Zebra", "")
        assertEquals("z.md", NoteSort.apply(withBlank, Sort.Latest).last().fileName)
    }
}
