package app.wispnotes.note

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateTest {
    private val clipperFile = """
        {
          "schemaVersion": "0.1.0",
          "name": "Books",
          "behavior": "create",
          "noteContentFormat": "# {{title}}\n\n{{content}}",
          "properties": [
            { "name": "author", "value": "{{author}}", "type": "text" },
            { "name": "published", "value": "{{published}}", "type": "date" },
            { "name": "tags", "value": "book, reading", "type": "multitext" }
          ],
          "triggers": ["https://books.example", "/^https:\\/\\/goodreads\\.com\\//"],
          "noteNameFormat": "{{title}}",
          "path": "Books"
        }
    """.trimIndent()

    @Test fun readsAClipperFile() {
        val template = Template.fromJson(clipperFile).getOrThrow()
        assertEquals("Books", template.name)
        assertEquals(TemplateBehavior.CREATE, template.behavior)
        assertEquals(3, template.properties.size)
        assertEquals(PropertyType.DATE, template.properties[1].type)
        assertEquals(2, template.triggers.size)
        assertEquals("Books", template.path)
    }

    @Test fun writesTheClipperShapeBack() {
        val template = Template.fromJson(clipperFile).getOrThrow()
        val json = JSONObject(template.toJson())
        assertEquals("0.1.0", json.getString("schemaVersion"))
        assertEquals("Books", json.getString("name"))
        assertEquals("create", json.getString("behavior"))
        assertEquals("{{title}}", json.getString("noteNameFormat"))
        assertEquals("Books", json.getString("path"))
        assertEquals(3, json.getJSONArray("properties").length())
        assertEquals("{{author}}", json.getJSONArray("properties").getJSONObject(0).getString("value"))
    }

    @Test fun dailyBehaviorsDropTheNoteNameAndFolder() {
        val template = Template(name = "Daily", noteContentFormat = "x", behavior = TemplateBehavior.APPEND_DAILY)
        val json = JSONObject(template.toJson())
        assertFalse(json.has("noteNameFormat"))
        assertFalse(json.has("path"))
        assertEquals("append-daily", json.getString("behavior"))
    }

    @Test fun defaultsAreClipperFiles() {
        assertEquals(2, Templates.builtIn.size)
        val article = JSONObject(Templates.article.toJson())
        assertEquals("0.1.0", article.getString("schemaVersion"))
        assertTrue(Templates.article.properties.any { it.name == "tags" })
        assertTrue(Templates.reel.triggers.any { it.startsWith("https://www.instagram.com/reel/") })
        // The first template is the fallback, as in the clipper.
        assertEquals("Article", Templates.builtIn.first().name)
    }

    @Test fun aBrokenFileIsAFailureNotACrash() {
        assertTrue(Template.fromJson("not json").isFailure)
        assertTrue(Template.fromJson("{}").isFailure)
        assertTrue(Template.fromJson("""{"name":"x"}""").isFailure)
    }

    @Test fun aMissingBehaviorIsCreate() {
        val template = Template.fromJson("""{"name":"x","noteContentFormat":"y"}""").getOrThrow()
        assertEquals(TemplateBehavior.CREATE, template.behavior)
        assertEquals("{{title}}", template.noteNameFormat)
        assertEquals("Inbox", template.path)
    }

    @Test fun reservedKeysAreTheOnesTheIndexReads() {
        assertTrue(Template.RESERVED.contains("title"))
        assertTrue(Template.RESERVED.contains("source"))
        assertTrue(Template.RESERVED.contains("wisp_template"))
        assertFalse(Template.RESERVED.contains("tags")) // merged, not reserved
    }
}
