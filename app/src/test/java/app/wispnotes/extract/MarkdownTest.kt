package app.wispnotes.extract

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    private fun md(html: String, base: String = "https://example.com/") =
        Markdown.from(Jsoup.parse(html, base).body())

    @Test fun keepsTheShapeOfAnArticle() {
        val out = md("<h2>Part two</h2><p>Some <b>bold</b> and <i>soft</i> text.</p>")!!
        assertEquals("## Part two\n\nSome **bold** and *soft* text.", out)
    }

    @Test fun headingsUseTheirLevel() {
        assertEquals("# One", md("<h1>One</h1>"))
        assertEquals("### Three", md("<h3>Three</h3>"))
        assertEquals("###### Six", md("<h6>Six</h6>"))
    }

    @Test fun listsKeepTheirMarkers() {
        assertEquals("- a\n- b", md("<ul><li>a</li><li>b</li></ul>"))
        assertEquals("1. a\n2. b", md("<ol><li>a</li><li>b</li></ol>"))
        assertEquals("- a\n  - b", md("<ul><li>a<ul><li>b</li></ul></li></ul>"))
    }

    @Test fun linksKeepTheirAddress() {
        assertEquals("[a link](https://example.com/x)", md("""<a href="/x">a link</a>"""))
        assertEquals("a link", md("""<a href="#top">a link</a>""")) // an in-page anchor is noise
        assertEquals("[https://example.com/x](https://example.com/x)", md("""<a href="/x"></a>"""))
    }

    @Test fun imagesStayLinksAndDataUrisAreDropped() {
        assertEquals("![pic](https://example.com/i.png)", md("""<img src="/i.png" alt="pic">"""))
        assertEquals("", md("""<img src="data:image/png;base64,AAAA">"""))
    }

    @Test fun codeIsFenced() {
        assertEquals("```\na < b\n```", md("<pre><code>a &lt; b</code></pre>"))
        assertEquals("`x()`", md("<code>x()</code>"))
    }

    @Test fun quotesAndRules() {
        assertEquals("> one\n> two", md("<blockquote><p>one</p><p>two</p></blockquote>"))
        assertEquals("---", md("<hr>"))
    }

    @Test fun tablesBecomeGfm() {
        val out = md("<table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table>")!!
        assertEquals("| A | B |\n| --- | --- |\n| 1 | 2 |", out)
    }

    @Test fun neverLeaksScriptsStylesOrNavigation() {
        val out = md("<nav><a href=\"/\">Home</a></nav><script>bad()</script><style>.x{}</style><p>Body.</p>")!!
        assertEquals("Body.", out)
        assertFalse(out.contains("Home"))
        assertFalse(out.contains("bad()"))
    }

    @Test fun nothingToSayIsNull() {
        assertNull(md("<div> </div>"))
        assertNull(md(""))
    }

    @Test fun collapsesTheIndentationHtmlIsFullOf() {
        val out = md("<p>\n   many\n   spaces   here\n</p>")!!
        assertEquals("many spaces here", out)
        assertTrue(out.none { it == '\u00a0' })
    }
}
