package app.wispnotes.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatePickTest {
    private val article = Template(name = "Article", noteContentFormat = "{{content}}")
    private val reel = Template(
        name = "Reel",
        noteContentFormat = "{{content}}",
        triggers = listOf("https://www.instagram.com/reel/"),
    )
    private val regex = Template(
        name = "GitHub",
        noteContentFormat = "{{content}}",
        triggers = listOf("/^https:\\/\\/github\\.com\\/[^/]+\\/[^/]+${'$'}/"),
    )
    private val schema = Template(
        name = "Recipe",
        noteContentFormat = "{{content}}",
        triggers = listOf("schema:@Recipe"),
    )
    private val all = listOf(article, reel, regex, schema)

    @Test fun aPrefixMatches() {
        assertEquals("Reel", TemplatePick.pick(all, "https://www.instagram.com/reel/abc/")?.name)
    }

    @Test fun aRegexMatchesTheWholeUrl() {
        assertEquals("GitHub", TemplatePick.pick(all, "https://github.com/hawkaii/wisp")?.name)
        assertFalse(TemplatePick.matches("/^https:\\/\\/github\\.com\\/[^/]+\\/[^/]+${'$'}/", "https://github.com/hawkaii"))
    }

    @Test fun schemaMatchesTheTypesOnThePage() {
        assertEquals("Recipe", TemplatePick.pick(all, "https://cook.example/x", listOf("Recipe"))?.name)
        // A type no trigger names matches nothing: the sheet offers a template instead.
        assertNull(TemplatePick.pick(all, "https://cook.example/x", listOf("NewsArticle")))
    }

    @Test fun nothingMatchingIsNull() {
        assertNull(TemplatePick.pick(all, "https://example.com/post"))
        assertNull(TemplatePick.pick(emptyList(), "https://example.com"))
    }

    @Test fun everyKindOfTriggerIsUnderstood() {
        assertTrue(TemplatePick.matches("https://a.com", "https://a.com/b"))
        assertTrue(TemplatePick.matches("/a\\.com/", "https://a.com/b"))
        assertTrue(TemplatePick.matches("schema:@Article", "https://a.com", listOf("Article")))
        assertFalse(TemplatePick.matches("https://b.com", "https://a.com"))
        assertFalse(TemplatePick.matches("", "https://a.com"))
        assertFalse(TemplatePick.matches("/(broken/", "https://a.com"))
    }

    /**
     * The maker builds the trigger from the shared URL. This test copies that rule verbatim: if the
     * two disagree, every template made from a share is dead on arrival.
     */
    @Test fun `the trigger the maker writes matches its own link`() {
        val url = "https://www.instagram.com/reel/ABC123/"
        val host = java.net.URI(url).host.orEmpty().removePrefix("www.")
        val trigger = "https://$host/"
        assertTrue(TemplatePick.matches(trigger, url))
    }
}
