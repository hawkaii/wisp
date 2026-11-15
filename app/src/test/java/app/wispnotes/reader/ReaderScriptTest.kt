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
}
