package app.wispnotes.note

import app.wispnotes.extract.Extracted
import app.wispnotes.extract.Merge
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeTest {
    private val url = "https://youtu.be/abc"
    private fun stub(title: String = "youtu.be", caption: String = "") =
        Note.render(NoteDraft(Source.detect(url), url, title, LocalDate.of(2026, 11, 13), caption))

    private fun body(content: String) = content.substring(content.indexOf("## My notes"))

    private val found = Extracted(title = "Real title", author = "Chan", thumbnail = "https://i/x.jpg", minutes = 4, text = "A description.")

    @Test fun movesPendingToDoneAndFillsOwnedKeys() {
        val out = Merge.apply(stub(), found)
        assertEquals("done", Frontmatter.text(out, "extracted"))
        assertEquals("Real title", Frontmatter.text(out, "title"))
        assertEquals("Chan", Frontmatter.text(out, "author"))
        assertEquals("https://i/x.jpg", Frontmatter.text(out, "thumbnail"))
        assertEquals("4", Frontmatter.text(out, "est_minutes"))
        assertTrue(out.endsWith("## Text\n\nA description.\n"))
    }

    @Test fun neverTouchesMyNotes() {
        val mine = stub().replace("## My notes\n", "## My notes\nMy own words: \"quoted\", ---, ## Text is mine?\n- a list\n")
        val out = Merge.apply(mine, found)
        val before = body(mine)
        assertTrue(body(out).startsWith(before.trimEnd('\n')))
        // Failed and partial runs too.
        assertTrue(body(Merge.apply(mine, Extracted())).startsWith(before.trimEnd('\n')))
        assertEquals("failed", Frontmatter.text(Merge.apply(mine, Extracted()), "extracted"))
        assertEquals("partial", Frontmatter.text(Merge.apply(mine, Extracted(thumbnail = "https://i/y.jpg")), "extracted"))
    }

    @Test fun keepsUserTitleKeysAndTags() {
        val edited = stub(title = "My own title")
            .replace("tags: [video, youtube]", "tags: [video, youtube, mine, \"to read\"]")
            .replace("est_minutes:", "est_minutes: 30")
            .replace("status: queued", "status: queued\nmood: spooky")
        val out = Merge.apply(edited, found)
        assertEquals("My own title", Frontmatter.text(out, "title"))
        assertEquals("30", Frontmatter.text(out, "est_minutes"))
        assertEquals("spooky", Frontmatter.text(out, "mood"))
        assertEquals(listOf("video", "youtube", "mine", "to read"), Frontmatter.tags(out))
    }

    @Test fun addsSourceTagsWithoutRemovingUserTags() {
        val noSourceTags = stub().replace("tags: [video, youtube]", "tags: [mine]")
        assertEquals(listOf("mine", "video", "youtube"), Frontmatter.tags(Merge.apply(noSourceTags, Extracted())))
        // Block-list tags written by Obsidian survive and stay readable.
        val block = stub().replace("tags: [video, youtube]", "tags:\n  - mine\n  - video")
        val out = Merge.apply(block, Extracted())
        assertEquals(listOf("mine", "video", "youtube"), Frontmatter.tags(out))
        assertTrue(!out.contains("  - mine"))
    }

    @Test fun placeholderTitlesAreReplaced() {
        for (t in listOf("Saved link", "youtu.be", url)) {
            assertEquals(t, "Real title", Frontmatter.text(Merge.apply(stub(t), found), "title"))
        }
    }

    @Test fun pastedCaptionIsKept() {
        val out = Merge.apply(stub(caption = "my pasted caption"), found)
        assertTrue(out.contains("my pasted caption"))
        assertTrue(!out.contains("A description."))
        assertEquals(1, Regex("## Text").findAll(out).count())
    }

    @Test fun rerunIsStable() {
        val once = Merge.apply(stub(), found)
        assertEquals(once, Merge.apply(once, found))
    }

    @Test fun noFrontmatterIsLeftAlone() {
        assertEquals("just text", Merge.apply("just text", found).substringBefore("\n\n## Text"))
    }

    @Test fun parsesFrontmatterEdgeCases() {
        val c = "---\ntitle: \"A: b\"\nurl: \"https://a.com/x?y=1\"\nest_minutes:\nrating:\n---\nbody: not a key\n"
        assertEquals("A: b", Frontmatter.text(c, "title"))
        assertEquals("https://a.com/x?y=1", Frontmatter.text(c, "url"))
        assertEquals(null, Frontmatter.text(c, "est_minutes"))
        assertEquals(null, Frontmatter.text(c, "body"))
        assertEquals("---\ntitle: \"A: b\"\nurl: \"https://a.com/x?y=1\"\nest_minutes: 5\nrating:\nextracted: done\n---\nbody: not a key\n",
            Frontmatter.set(c, mapOf("est_minutes" to "5", "extracted" to "done")))
    }
}
