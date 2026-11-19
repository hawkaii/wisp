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

    @Test fun defaultsAvoidTheNoteNameAndFolderWhenDaily() {
        val template = Template(name = "Daily", noteContentFormat = "x", behavior = TemplateBehavior.APPEND_DAILY)
        val json = JSONObject(template.toJson())
        assertFalse(json.has("noteNameFormat"))
        assertFalse(json.has("path"))
        assertEquals("append-daily", json.getString("behavior"))
    }

    @Test fun aFolderIsOneName() {
        val slash = Template.fromJson("""{"name":"x","noteContentFormat":"y","path":"Inbox/Notes"}""")
        assertTrue(slash.isFailure)
        assertTrue(slash.exceptionOrNull()!!.message!!.contains("no slashes"))
        assertTrue(Template.fromJson("""{"name":"x","noteContentFormat":"y","path":"../Secrets"}""").isFailure)
        assertTrue(Template.fromJson("""{"name":"x","noteContentFormat":"y","path":".hidden"}""").isFailure)
    }

    @Test fun onlyCreateIsSupported() {
        val daily = Template.fromJson("""{"name":"x","noteContentFormat":"y","behavior":"append-daily"}""")
        assertTrue(daily.isFailure)
        assertEquals("Only behavior \"create\" is supported.", daily.exceptionOrNull()?.message)
        assertTrue(Template.fromJson("""{"name":"x","noteContentFormat":"y","behavior":"nope"}""").isFailure)
    }

    @Test fun thePageIsNeededOnlyForItsText() {
        assertTrue(Template(name = "x", noteContentFormat = "{{content}}").needsPage())
        assertTrue(Template(name = "x", noteContentFormat = "{{title}}\n\n{{transcript}}").needsPage())
        assertFalse(Template(name = "x", noteContentFormat = "{{title}}").needsPage())
    }

    @Test fun theDefaultTemplateIsAValidFile() {
        val default = Template.default()
        assertEquals("Default", default.name)
        assertEquals("Clippings", default.path)
        assertEquals("create", JSONObject(default.toJson()).getString("behavior"))
        // The file the app seeds must read back as the same template.
        assertEquals(default, Template.fromJson(default.toJson()).getOrThrow())
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
        assertEquals("Clippings", template.path)
    }

    @Test fun reservedKeysAreTheOnesTheIndexReads() {
        assertTrue(Template.RESERVED.contains("title"))
        assertTrue(Template.RESERVED.contains("source"))
        assertTrue(Template.RESERVED.contains("wisp_template"))
        assertFalse(Template.RESERVED.contains("tags")) // merged, not reserved
    }
}
