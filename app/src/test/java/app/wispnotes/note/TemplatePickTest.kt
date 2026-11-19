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
     * The maker builds the trigger from the shared URL. If the two disagree, every template made
     * from a share is dead on arrival. This failed on main before the fix, which is why it is here.
     */
    @Test fun `the maker's trigger matches the link it came from`() {
        listOf(
            "https://www.instagram.com/reel/ABC123/",
            "https://www.threads.net/@someone/post/abc",
            "https://www.youtube.com/watch?v=abc",
            "https://www.reddit.com/r/notes/comments/abc/title/",
            "https://example.com/a/b",
        ).forEach { url ->
            assertTrue(url, TemplateSeed.triggersFor(url).any { TemplatePick.matches(it, url) })
        }
    }

    @Test fun `a trigger without www still matches a link with it`() {
        assertTrue(TemplatePick.matches("https://instagram.com/", "https://www.instagram.com/reel/x"))
        assertTrue(TemplatePick.matches("https://www.instagram.com/", "https://instagram.com/reel/x"))
    }

    @Test fun `the seed does not match a different site`() {
        assertFalse(TemplateSeed.triggersFor("https://www.instagram.com/").any { TemplatePick.matches(it, "https://www.threads.net/x") })
    }


    /** YouTube is shared as `youtu.be` from its app and as `youtube.com` from a browser. */
    @Test fun `one youtube template matches both link shapes`() {
        val triggers = TemplateSeed.triggersFor("https://youtu.be/ABC123")
        listOf(
            "https://youtu.be/ABC123",
            "https://www.youtube.com/watch?v=ABC123",
            "https://m.youtube.com/watch?v=ABC123",
        ).forEach { url ->
            assertTrue(url, triggers.any { TemplatePick.matches(it, url) })
        }
        assertFalse(triggers.any { TemplatePick.matches(it, "https://www.instagram.com/reel/x") })
    }
}
