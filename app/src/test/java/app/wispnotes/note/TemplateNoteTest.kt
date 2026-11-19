package app.wispnotes.note

import app.wispnotes.extract.Extracted
import app.wispnotes.extract.Merge
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateNoteTest {
    private val info = Source.detect("https://example.com/post")

    private fun draft(title: String = "Memory", caption: String = "") =
        NoteDraft(info, "https://example.com/post", title, LocalDate.of(2026, 11, 19), caption)

    private val template = Template(
        name = "Notes",
        noteContentFormat = "{{content}}",
        properties = listOf(
            TemplateProperty("author", "{{author}}"),
            TemplateProperty("site", "{{site}}"),
            TemplateProperty("created", "{{date}}", PropertyType.DATE),
            TemplateProperty("tags", "clippings", PropertyType.MULTITEXT),
            TemplateProperty("title", "the template must not win"),
        ),
        noteNameFormat = "{{title}} {{source}}",
    )

    @Test fun aSaveRecordsTheTemplateAndItsProperties() {
        val out = Note.render(draft(), template)

        assertEquals("Notes", Frontmatter.text(out, "wisp_template"))
        assertEquals(listOf("article", "clippings"), Frontmatter.tags(out))
        assertEquals("2026-11-19", Frontmatter.text(out, "created"))
        assertEquals("Memory", Frontmatter.text(out, "title")) // reserved
        assertNull(Frontmatter.text(out, "site")) // nothing to fill it yet
        assertTrue(out.contains("## My notes"))
        assertFalse(out.contains("## Text")) // extraction fills it, not the save
    }

    @Test fun theFileNameComesFromTheTemplate() {
        assertEquals("2026-11-19-memory-web.md", Note.fileName(draft(), template))
        assertEquals("2026-11-19-memory.md", Note.fileName(draft()))
    }

    @Test fun extractionRendersTheBodyIntoText() {
        val found = Extracted(title = "Memory", author = "Jane Doe", site = "Example", text = "# How memory works\n\nBody.")
        val out = Merge.apply(Note.render(draft(), template), found, template)

        assertEquals("Jane Doe", Frontmatter.text(out, "author"))
        assertEquals("Example", Frontmatter.text(out, "site"))
        assertTrue(out.contains("## Text\n\n# How memory works\n\nBody."))
        assertEquals(1, Regex("## Text").findAll(out).count())
        assertEquals("done", Frontmatter.text(out, "extracted"))
    }

    @Test fun myNotesSurviveEveryRun() {
        val mine = Note.render(draft(), template).replace("## My notes\n", "## My notes\nMy words.\n")
        val out = Merge.apply(mine, Extracted(title = "Memory", text = "Body."), template)
        assertTrue(out.contains("## My notes\nMy words."))
        assertEquals(1, Regex("## Text").findAll(out).count())
    }

    @Test fun aTemplateCannotTakeOverAnAppKeyOrTheUsersValue() {
        val edited = Note.render(draft(), template).replace("title: \"Memory\"", "title: \"Mine\"")
        val out = Merge.apply(edited, Extracted(title = "Their title", text = "Body."), template)
        assertEquals("Mine", Frontmatter.text(out, "title"))
    }

    @Test fun theUsersTagsStay() {
        val edited = Note.render(draft(), template).replace("tags: [article, clippings]", "tags: [mine]")
        val out = Merge.apply(edited, Extracted(title = "Memory", text = "Body."), template)
        assertEquals(listOf("mine", "article", "clippings"), Frontmatter.tags(out))
    }

    @Test fun templateHeadingsMoveOneLevelDown() {
        val deeper = template.copy(noteContentFormat = "## Summary\n\n{{content}}")
        val out = Merge.apply(Note.render(draft(), deeper), Extracted(title = "Memory", text = "Body."), deeper)
        assertTrue(out.contains("### Summary"))
        assertFalse(out.contains("\n## Summary"))
    }

    @Test fun aPlainNoteKeepsTheOldShape() {
        val out = Merge.apply(Note.render(draft()), Extracted(title = "Memory", text = "Body."))
        assertTrue(out.contains("## Text\n\nBody."))
        assertNull(Frontmatter.text(out, "wisp_template"))
    }
}
