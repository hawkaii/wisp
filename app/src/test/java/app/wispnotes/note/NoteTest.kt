package app.wispnotes.note

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteTest {
    @Test fun detectsSources() {
        assertEquals("reel" to "instagram", Source.detect("https://www.instagram.com/reel/abc/?vrfl=1").let { it.type to it.source })
        assertEquals("post", Source.detect("https://instagram.com/p/xyz/").type)
        assertEquals("tweet", Source.detect("https://x.com/u/status/1").type)
        assertEquals("tweet", Source.detect("https://twitter.com/u/status/1").type)
        assertEquals("video", Source.detect("https://youtu.be/abc").type)
        assertEquals("video", Source.detect("https://m.youtube.com/watch?v=abc").type)
        assertEquals("short", Source.detect("https://www.youtube.com/shorts/abc").type)
        assertEquals("imdb", Source.detect("https://www.imdb.com/title/tt1/").source)
        assertEquals("letterboxd", Source.detect("https://letterboxd.com/film/x/").source)
        assertEquals(listOf("article"), Source.detect("https://example.com/a").tags)
        assertEquals(listOf("video", "youtube"), Source.detect("https://youtu.be/abc").tags)
        assertEquals("web", Source.detect("not a url").source)
    }

    @Test fun cleansTrackingParams() {
        assertEquals("https://www.instagram.com/reel/abc/", Source.cleanUrl("https://www.instagram.com/reel/abc/?vrfl=xyz"))
        assertEquals("https://youtu.be/abc", Source.cleanUrl("https://youtu.be/abc?si=123"))
        assertEquals(
            "https://youtube.com/watch?v=abc&t=5",
            Source.cleanUrl("https://youtube.com/watch?v=abc&utm_source=x&t=5&si=1"),
        )
        assertEquals("https://a.com/p#frag", Source.cleanUrl("https://a.com/p?fbclid=1#frag"))
        assertEquals("https://a.com/p", Source.cleanUrl("https://a.com/p"))
    }

    @Test fun extractsUrl() {
        assertEquals("https://a.com/x", Source.extractUrl("Look at this https://a.com/x."))
        assertNull(Source.extractUrl("no link here"))
    }

    @Test fun slugs() {
        assertEquals("how-memory-really-works", Note.slug("How memory REALLY works!"))
        assertEquals("note", Note.slug("???"))
        assertEquals(50, Note.slug("a".repeat(80)).length)
    }

    @Test fun writesFrontmatter() {
        val d = NoteDraft(Source.detect("https://youtu.be/abc"), "https://youtu.be/abc", "Say \"hi\"\nnow", LocalDate.of(2026, 11, 13))
        assertEquals("2026-11-13-say-hi-now.md", Note.fileName(d))
        val expected = """
            ---
            type: video
            source: youtube
            url: "https://youtu.be/abc"
            title: "Say \"hi\" now"
            saved: 2026-11-13
            status: queued
            est_minutes:
            rating:
            tags: [video, youtube]
            extracted: pending
            ---

            ## My notes

        """.trimIndent() + "\n"
        assertEquals(expected, Note.render(d))
    }
}
