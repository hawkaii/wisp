package app.wispnotes.note

import app.wispnotes.extract.Extracted
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureTest {
    private val draft = NoteDraft(
        Source.detect("https://example.com/post"),
        "https://example.com/post",
        "A title",
        LocalDate.of(2026, 11, 19),
        "the shared caption",
    )

    @Test fun theCaptionIsItsOwnVariable() {
        val variables = Capture.of(draft).variables()
        assertEquals("the shared caption", variables["caption"])
        // The page text is not the caption: content waits for the page.
        assertEquals("", variables["content"])
    }

    @Test fun thePageFillsTheContent() {
        val found = Extracted(title = "Page title", text = "Page body.", minutes = 3)
        val variables = Capture.of(draft, found).variables()
        assertEquals("Page body.", variables["content"])
        assertEquals("Page title", variables["title"])
        assertEquals("the shared caption", variables["caption"])
    }
}
