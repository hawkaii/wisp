package app.wispnotes.vault

import app.wispnotes.index.RawNote
import app.wispnotes.note.Frontmatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicatesTest {
    private fun note(file: String, url: String?, title: String? = null) = RawNote(
        file,
        buildString {
            appendLine("---")
            appendLine("type: reel")
            if (url != null) appendLine("url: ${Frontmatter.quote(url)}")
            if (title != null) appendLine("title: ${Frontmatter.quote(title)}")
            appendLine("---")
            appendLine()
            appendLine("## My notes")
        },
    )

    @Test
    fun `the same url is found`() {
        val notes = listOf(note("a.md", "https://www.instagram.com/reel/ABC/", "Three articles"))
        val found = Duplicates.match(notes, "https://www.instagram.com/reel/ABC/")
        assertEquals("a.md", found?.path)
        assertEquals("Three articles", found?.title)
    }

    @Test
    fun `tracking params do not make a new link`() {
        val notes = listOf(note("a.md", "https://www.instagram.com/reel/ABC/?igsh=xyz"))
        assertEquals("a.md", Duplicates.match(notes, "https://www.instagram.com/reel/ABC/")?.path)
    }

    @Test
    fun `a different link is not a duplicate`() {
        val notes = listOf(note("a.md", "https://www.instagram.com/reel/ABC/"))
        assertNull(Duplicates.match(notes, "https://www.instagram.com/reel/XYZ/"))
    }

    @Test
    fun `a note without a title falls back to its file name`() {
        val notes = listOf(note("2026-11-15-essays.md", "https://example.com/a"))
        assertEquals("2026-11-15-essays", Duplicates.match(notes, "https://example.com/a")?.title)
    }

    @Test
    fun `a note without a url never matches`() {
        assertNull(Duplicates.match(listOf(note("a.md", null)), "https://example.com/a"))
    }

    @Test
    fun `a blank url never matches`() {
        assertNull(Duplicates.match(listOf(note("a.md", "https://example.com/a")), "   "))
    }

    @Test
    fun `a clipped note is matched by the address it keeps in source`() {
        val clipped = RawNote(
            "Clippings/on-taste.md",
            """
            ---
            title: "On taste"
            source: "https://substack.com/p/on-taste"
            ---
            """.trimIndent(),
        )
        val found = Duplicates.match(listOf(clipped), "https://substack.com/p/on-taste?utm_source=share")
        assertEquals("Clippings/on-taste.md", found?.path)
        assertEquals("On taste", found?.title)
    }
}
