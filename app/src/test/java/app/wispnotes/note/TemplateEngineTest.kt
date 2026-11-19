package app.wispnotes.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateEngineTest {
    private fun render(template: String, variables: Map<String, Any?> = emptyMap()) =
        TemplateEngine.render(template, variables)

    private val link = mapOf("text" to "Article A", "href" to "https://example.com/a")

    @Test fun readsVariables() {
        assertEquals("Hi Parthib", render("Hi {{name}}", mapOf("name" to "Parthib")))
        assertEquals("[]", render("[{{nope}}]"))
        assertEquals("4", render("{{n}}", mapOf("n" to 4)))
        assertEquals("4", render("{{n}}", mapOf("n" to 4.0)))
    }

    @Test fun readsDottedPathsAndIndexes() {
        val variables = mapOf(
            "link" to link,
            "links" to listOf(link, mapOf("text" to "B", "href" to "https://example.com/b")),
        )
        assertEquals("https://example.com/a", render("{{link.href}}", variables))
        assertEquals("B", render("{{links[1].text}}", variables))
    }

    /** Keys such as `meta:name:description` and `schema:@Article:name` are single keys, dots included. */
    @Test fun readsKeysThatLookLikePaths() {
        val variables = mapOf(
            "meta:name:description" to "A summary.",
            "schema:@Article:author.name" to "Ada",
        )
        assertEquals("A summary.", render("{{meta:name:description}}", variables))
        assertEquals("Ada", render("{{schema:@Article:author.name}}", variables))
    }

    @Test fun filtersTheWayClipperTemplatesDo() {
        assertEquals("HI", render("{{v|upper}}", mapOf("v" to "hi")))
        assertEquals("hi", render("{{v|lower}}", mapOf("v" to "HI")))
        assertEquals("x", render("{{nope|default:\"x\"}}"))
        assertEquals("kept", render("{{v|default:\"x\"}}", mapOf("v" to "kept")))
        assertEquals("2026-11-19", render("{{v|date:\"YYYY-MM-DD\"}}", mapOf("v" to "2026-11-19T10:00:00Z")))
        assertEquals("19 November 2026", render("{{v|date:\"DD MMMM YYYY\"}}", mapOf("v" to "2026-11-19")))
        assertEquals("bonono", render("{{v|replace:\"a\":\"o\"}}", mapOf("v" to "banana")))
        assertEquals("ab", render("{{v|slice:0,2}}", mapOf("v" to "abcdef")))
        assertEquals("a, b", render("{{v|join:\", \"}}", mapOf("v" to listOf("a", "b"))))
        assertEquals("- a\n- b", render("{{v|list}}", mapOf("v" to listOf("a", "b"))))
        assertEquals("[Go](https://x.y)", render("{{v|link:\"Go\"}}", mapOf("v" to "https://x.y")))
        assertEquals("[[Note]]", render("{{v|wikilink}}", mapOf("v" to "Note")))
        assertEquals("> a\n> b", render("{{v|blockquote}}", mapOf("v" to "a\nb")))
        assertEquals("2", render("{{v|length}}", mapOf("v" to listOf("a", "b"))))
        assertEquals("first", render("{{v|first}}", mapOf("v" to listOf("first", "second"))))
        assertEquals("plain", render("{{v|strip_tags}}", mapOf("v" to "<p>plain</p>")))
        assertEquals("## Hi", render("{{v|markdown}}", mapOf("v" to "<h2>Hi</h2>")))
    }

    @Test fun unknownFiltersAreANoOp() {
        assertEquals("hi", render("{{v|not_a_filter}}", mapOf("v" to "hi")))
    }

    @Test fun conditionals() {
        assertEquals("by Ada", render("{% if author %}by {{author}}{% endif %}", mapOf("author" to "Ada")))
        assertEquals("", render("{% if author %}by {{author}}{% endif %}", mapOf("author" to "")))
        assertEquals("none", render("{% if author %}yes{% else %}none{% endif %}"))
        assertEquals(
            "draft",
            render("{% if s == \"published\" %}live{% elseif s == \"draft\" %}draft{% else %}other{% endif %}", mapOf("s" to "draft")),
        )
        assertEquals("yes", render("{% if n > 2 and n < 5 %}yes{% endif %}", mapOf("n" to 3)))
        assertEquals("yes", render("{% if title contains \"memory\" %}yes{% endif %}", mapOf("title" to "How memory works")))
        assertEquals("yes", render("{% if not draft %}yes{% endif %}", mapOf("draft" to false)))
        assertEquals("yes", render("{% if (a or b) and c %}yes{% endif %}", mapOf("a" to false, "b" to true, "c" to true)))
    }

    @Test fun loopsOverTheAiLinks() {
        val template = "{% for l in links %}- [{{l.text}}]({{l.href}})\n{% endfor %}"
        val variables = mapOf("links" to listOf(link, mapOf("text" to "B", "href" to "https://example.com/b")))
        assertEquals("- [Article A](https://example.com/a)\n- [B](https://example.com/b)\n", render(template, variables))
    }

    @Test fun aLoopOverNothingRendersNothing() {
        assertEquals("[]", render("[{% for l in nope %}{{l}}{% endfor %}]"))
    }

    @Test fun setAndNestedLoops() {
        assertEquals("Ada", render("{% set who = author %}{{who}}", mapOf("author" to "Ada")))
        val template = "{% for l in links %}{% if l.text %}{{l.text}}{% endif %}{% endfor %}"
        assertEquals("AB", render(template, mapOf("links" to listOf(link, mapOf("text" to "B", "href" to "u")))))
    }

    @Test fun aBrokenTemplateNeverThrows() {
        assertEquals("{{", render("{{"))
        assertEquals("text", render("text{% if %}"))
        assertEquals("text", render("text{% endif %}"))
        assertEquals("", render("{{ }}"))
        assertEquals("", render("{{a}}"))
    }

    @Test fun comparisonUsesNumbersWhenItCan() {
        assertEquals("yes", render("{% if a == 10 %}yes{% endif %}", mapOf("a" to "10")))
        assertEquals("yes", render("{% if a != b %}yes{% endif %}", mapOf("a" to "1", "b" to "2")))
        assertTrue(render("{% if a >= 2 %}yes{% endif %}", mapOf("a" to 2)) == "yes")
    }
}
