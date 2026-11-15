package app.wispnotes.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderScriptTest {
    /**
     * The extension's prompt, word for word. It is asserted here because the wording is the difference
     * between meta.ai listing the article links and answering "no external links are mentioned":
     * asking for every "article, book, tool and person ... with links" is what worked on the same reel.
     */
    @Test fun promptIsTheOneTheExtensionUsed() {
        assertEquals(
            "Summarise this reel in 5 bullets.\n" +
                "List every article, book, tool and person it mentions, with links.\n" +
                "Then give the full transcript.",
            ReaderScript.PROMPT,
        )
    }

    @Test fun startCallCarriesTheReelAndKeepsThePromptOnOneJavascriptLine() {
        val js = ReaderScript.start("https://www.instagram.com/reel/Dfsp4y0gEXH/")
        assertTrue(js.contains("\"https://www.instagram.com/reel/Dfsp4y0gEXH/\""))
        assertTrue(js.contains("Summarise this reel in 5 bullets."))
        assertTrue(js.contains("bullets.\\nList every article, book, tool and person"))
        assertFalse(js.contains("\n")) // a raw newline would break the string literal
    }

    @Test fun quotingCannotCloseTheCallOrTheTag() {
        assertEquals("\"a\\\"b\"", ReaderScript.quote("a\"b"))
        assertEquals("\"a\\\\b\"", ReaderScript.quote("a\\b"))
        assertEquals("\"a\\u003cb\\u003e\"", ReaderScript.quote("a<b>"))
        assertEquals("\"a\\u0026b\"", ReaderScript.quote("a&b"))
        assertEquals("\"one\\ntwo\"", ReaderScript.quote("one\ntwo"))
    }

    @Test fun readsTheStateThePageSendsBack() {
        val raw = """
            {"stage":"done","error":null,"answer":"5 bullets","links":[
              {"url":"https://example.com/a","text":"Article A"},
              {"url":"https://example.com/b","text":""}],
             "chatUrl":"https://www.meta.ai/prompt/abc","composer":true,"login":false,
             "page":"https://www.meta.ai/prompt/abc"}
        """.trimIndent()
        val s = ReaderState.parse(raw)
        assertNotNull(s)
        requireNotNull(s)
        assertTrue(s.done)
        assertFalse(s.failed)
        assertEquals("5 bullets", s.answer)
        assertEquals(2, s.links.size)
        assertEquals("https://example.com/a", s.links[0].url)
        assertEquals("Article A", s.links[0].text)
        assertEquals("https://www.meta.ai/prompt/abc", s.chatUrl)
        assertNull(s.error)
    }

    @Test fun emptyLinksAndMissingChatAreStillAnAnswer() {
        val s = ReaderState.parse("""{"stage":"done","answer":"5 bullets","links":[]}""")
        requireNotNull(s)
        assertTrue(s.done)
        assertEquals(0, s.links.size)
        assertNull(s.chatUrl)
        assertNull(s.error)
    }

    @Test fun noStateIsNotAnAnswer() {
        assertNull(ReaderState.parse(null))
        assertNull(ReaderState.parse(""))
        assertNull(ReaderState.parse("null"))
        assertNull(ReaderState.parse("{\"stage\":\"done\",\"answer\":\"\"}")?.takeIf { it.done })
    }

    @Test fun aFailedRunSaysWhyInOneLine() {
        val login = ReaderState.idle.copy(stage = "failed", error = "needs_login")
        assertTrue(login.failed)
        assertTrue(login.label().contains("sign-in"))
        assertEquals("This took more than six minutes. Try again.", ReaderState.idle.copy(stage = "failed", error = "timeout").label())
        assertEquals("Reading the reel. About 30 seconds.", ReaderState.idle.copy(stage = "reading").label())
    }

    /** The mobile page has no `data-testid` composer, and a fresh guest is asked for a birth year. */
    @Test fun theBirthYearGateIsAStageTheUserFinishesNotAFailure() {
        val s = ReaderState.parse("""{"stage":"needs_setup","gate":true,"composer":false,"answers":0,"links":[]}""")
        requireNotNull(s)
        assertTrue(s.waitingForUser)
        assertFalse(s.failed)
        assertFalse(s.done)
        assertTrue(s.gate)
        assertTrue(s.label().contains("birth year"))
        assertTrue(s.label().contains("Continue"))
    }

    @Test fun aGateFollowedByAnAnswerIsVisibleToTheApp() {
        val s = ReaderState.parse("""{"stage":"needs_setup","gate":true,"answers":1,"links":[]}""")
        requireNotNull(s)
        // the page carried on by itself; the watcher sees an answer and harvests it
        assertTrue(s.waitingForUser)
        assertEquals(1, s.answers)
    }

    @Test fun resumeIsQuotedLikeStart() {
        val js = ReaderScript.resume("https://www.instagram.com/reel/Dfsp4y0gEXH/")
        assertTrue(js.contains("window.__wisp.resume("))
        assertTrue(js.contains("\"https://www.instagram.com/reel/Dfsp4y0gEXH/\""))
        assertFalse(js.contains("\n"))
    }

    @Test fun theMobilePageIsTriedAfterTheDesktopOne() {
        // the one line that broke the first build: mobile meta.ai has no data-testid composer
        assertTrue(ReaderScript.INSTALL.contains("input[aria-label=\"Ask Meta AI\"]"))
        assertTrue(ReaderScript.INSTALL.contains("button[aria-label=\"Send\"]"))
        assertTrue(ReaderScript.INSTALL.contains("date of birth"))
        // the login signal is meta.ai's own button, not text guessed from the page
        assertTrue(ReaderScript.INSTALL.contains("login-button"))
        // Send is a real touch from Kotlin, not a scripted click
        assertTrue(ReaderScript.INSTALL.contains("run.tap = rectOf(send)"))
        assertTrue(ReaderScript.INSTALL.contains("tapped: function"))
    }

    /** The mobile Send ignores scripted clicks, so the page hands Kotlin a point for a real touch. */
    @Test fun aTapRequestCarriesThePointAndTheScale() {
        val s = ReaderState.parse("""{"stage":"sending","tap":{"x":358,"y":598.5,"dpr":2.625},"links":[]}""")
        requireNotNull(s)
        val tap = requireNotNull(s.tap)
        assertEquals(358f, tap.x, 0.01f)
        assertEquals(598.5f, tap.y, 0.01f)
        assertEquals(2.625f, tap.dpr, 0.001f)
        assertNull(ReaderState.parse("""{"stage":"reading","tap":null}""")?.tap)
    }
}
