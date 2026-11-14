package app.wispnotes.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun transcriptFixture(name: String): String =
    TranscriptTest::class.java.getResource("/fixtures/$name")!!.readText()

class TranscriptTest {
    @Test fun picksEnglishTrackAndParsesJson3() {
        val track = TranscriptParser.captionTrack(transcriptFixture("yt_transcript_player.html"), "de-DE")!!
        assertEquals("en", track.languageCode)
        val segments = TranscriptParser.parseJson3(transcriptFixture("yt_transcript.json3"))
        assertEquals(listOf(TranscriptSegment(0, "Hello world."), TranscriptSegment(2, "This is a test.")), segments.take(2))
        assertEquals(listOf(TranscriptLine(0, "Hello world."), TranscriptLine(2, "This is a test."), TranscriptLine(6, "A tiny piece")), TranscriptParser.mergeSegments(segments))
    }

    @Test fun parsesXmlAndRendersTimestampLinks() {
        val lines = TranscriptParser.mergeSegments(TranscriptParser.parseXml(transcriptFixture("yt_transcript.xml")))
        assertEquals("Hello & welcome.", lines.first().text)
        assertEquals("**[00:00](https://youtu.be/abc?t=0)** Hello & welcome.",
            "**[${formatTranscriptTimestamp(lines.first().startSeconds)}](https://youtu.be/abc?t=${lines.first().startSeconds})** ${lines.first().text}")
        assertEquals(2, lines.size)
    }

    @Test fun parsesTimedTextParagraphsWithWordSpans() {
        val segments = TranscriptParser.parseXml(transcriptFixture("yt_transcript_p.xml"))
        assertEquals(listOf(TranscriptSegment(0, "Hello from the talk. This is spoken text."), TranscriptSegment(2, "Here is another sentence.")), segments)
    }

    @Test fun noCaptionsReturnsNoTrack() {
        assertEquals(null, TranscriptParser.captionTrack(transcriptFixture("yt_no_captions.html")))
        assertTrue(TranscriptParser.parseCaptionBody("").isEmpty())
    }

    @Test fun parsesGeneratedMarkdown() {
        val lines = TranscriptParser.parseMarkdown("**[01:02](https://youtu.be/abc?t=62)** A line")
        assertEquals(listOf(TranscriptLine(62, "A line")), lines)
    }
}
