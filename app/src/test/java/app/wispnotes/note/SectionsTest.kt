package app.wispnotes.note

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionsTest {
    private val note = """
        ---
        type: article
        source: web
        title: "A page"
        status: queued
        rating:
        tags: [article]
        mood: spooky
        ---

        ## My notes

        first thought

        ## Text

        The article text.
        More text.
    """.trimIndent() + "\n"

    @Test fun readsTheUserSection() {
        assertEquals("first thought", Sections.myNotes(note))
        assertEquals("", Sections.myNotes("---\nsource: web\n---\n"))
    }

    @Test fun unchangedTextReturnsTheSameFile() {
        assertSame(note, Sections.withMyNotes(note, "first thought"))
        assertSame(note, Sections.withMyNotes(note, "  first thought\n\n"))
    }

    @Test fun editingChangesOnlyTheUserSection() {
        val out = Sections.withMyNotes(note, "line one\nline two")
        assertEquals(note.replace("first thought", "line one\nline two"), out)
    }

    @Test fun frontmatterEditsLeaveTheUserTextAndOtherKeysAlone() {
        val typed = Sections.withMyNotes(note, "line one\n\n- a *list*\n  indented\nline three")
        val out = Frontmatter.set(typed, Review.finishValues(4, LocalDate.of(2026, 11, 20)))
        assertEquals("line one\n\n- a *list*\n  indented\nline three", Sections.myNotes(out))
        assertEquals(Frontmatter.body(typed), Frontmatter.body(out)) // body byte for byte
        assertEquals("spooky", Frontmatter.text(out, "mood")) // a key the app does not own
        assertEquals("4", Frontmatter.text(out, "rating"))
        assertEquals("done", Frontmatter.text(out, "status"))
        assertEquals("2026-11-20", Frontmatter.text(out, "finished"))
        assertEquals("2026-11-23", Frontmatter.text(out, "next_review"))
        assertEquals("0", Frontmatter.text(out, "review_step"))
    }

    @Test fun userHeadingsInsideTheirNotesAreKeptNotDuplicated() {
        val text = "intro\n\n## Ideas\nbuy a candle"
        val once = Sections.withMyNotes(note, text)
        assertEquals(text, Sections.myNotes(once))
        assertEquals(once, Sections.withMyNotes(once, text))
        assertEquals(once, Sections.withMyNotes(once, Sections.myNotes(once)))
        assertTrue(once.endsWith("## Text\n\nThe article text.\nMore text.\n"))
        assertEquals(1, Regex("## Ideas").findAll(once).count())
    }

    @Test fun clearingAndRestoringKeepsTheRestOfTheFile() {
        val cleared = Sections.withMyNotes(note, "")
        assertEquals("", Sections.myNotes(cleared))
        assertTrue(cleared.contains("## Text\n\nThe article text."))
        assertEquals(note, Sections.withMyNotes(cleared, "first thought"))
    }

    @Test fun missingSectionIsAppendedAndNothingElseMoves() {
        val bare = "---\nsource: web\n---\n\nbody\n"
        val out = Sections.withMyNotes(bare, "hello")
        assertTrue(out.startsWith(bare.trimEnd('\n')))
        assertEquals("hello", Sections.myNotes(out))
        assertSame(bare, Sections.withMyNotes(bare, "  "))
    }

    @Test fun notesAtTheEndOfTheFile() {
        val n = "---\nsource: web\n---\n\n## Text\n\nabc\n\n## My notes\n\nold\n"
        assertEquals("---\nsource: web\n---\n\n## Text\n\nabc\n\n## My notes\n\nnew\n", Sections.withMyNotes(n, "new"))
    }

    @Test fun readableBodyHasNoFrontmatterOrUserNotes() {
        val r = Sections.readable(note)
        assertEquals("## Text\n\nThe article text.\nMore text.", r)
    }
}
