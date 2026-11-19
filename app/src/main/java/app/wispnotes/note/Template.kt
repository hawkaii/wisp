package app.wispnotes.note

import org.json.JSONArray
import org.json.JSONObject

/**
 * An Obsidian Web Clipper template, read from JSON.
 *
 * The format is the clipper's own export shape (`schemaVersion 0.1.0`), so a template file moves
 * between the two apps with no converter. `id` is not part of the format: the file name is the id.
 *
 * One deliberate difference, documented in [Templates]: the clipper's `noteContentFormat` is the whole
 * note body. Here the app owns the file (`## My notes`, `## Transcript`, `## AI`, frontmatter), so a
 * template's body is written inside the app-owned `## Text` section. Everything else — variables,
 * filters, conditionals, loops, properties, triggers — behaves the same.
 */
data class Template(
    val name: String,
    val noteContentFormat: String,
    val behavior: TemplateBehavior = TemplateBehavior.CREATE,
    val properties: List<TemplateProperty> = emptyList(),
    val triggers: List<String> = emptyList(),
    val noteNameFormat: String = "{{title}}",
    val path: String = "Inbox",
    val context: String? = null,
) {
    /** A file name for this template, used when the defaults are written into the vault. */
    fun fileName(): String = Note.slug(name) + ".json"

    /** The clipper's export shape and key order. */
    fun toJson(): String {
        val json = JSONObject()
        json.put("schemaVersion", SCHEMA_VERSION)
        json.put("name", name)
        json.put("behavior", behavior.wire)
        json.put("noteContentFormat", noteContentFormat)
        json.put(
            "properties",
            JSONArray().apply {
                properties.forEach { property ->
                    put(
                        JSONObject().apply {
                            put("name", property.name)
                            put("value", property.value)
                            put("type", property.type.wire)
                        }
                    )
                }
            },
        )
        json.put("triggers", JSONArray(triggers))
        // The clipper drops the note name and the folder for daily-note behaviors.
        if (behavior != TemplateBehavior.APPEND_DAILY && behavior != TemplateBehavior.PREPEND_DAILY) {
            json.put("noteNameFormat", noteNameFormat)
            json.put("path", path)
        }
        context?.let { json.put("context", it) }
        return json.toString(2)
    }

    companion object {
        const val SCHEMA_VERSION = "0.1.0"

        /** Keys the app owns. A template may not set them: the index, the duplicate check and the
         *  sections all read them, and a template that renamed one would break the library. */
        val RESERVED = setOf(
            "type", "source", "url", "title", "saved", "status",
            "extracted", "transcript", "extract_attempts",
            "est_minutes", "thumbnail", "rating", "finished", "next_review", "review_step",
            "wisp_template",
        )

        /** Never throws: a broken file in the vault must not stop a save. */
        fun fromJson(text: String): Result<Template> = runCatching {
            val json = JSONObject(text)

            val name = json.optString("name").trim()
            require(name.isNotEmpty()) { "A template needs a name." }

            val content = json.optString("noteContentFormat")
            require(content.isNotBlank()) { "A template needs noteContentFormat." }

            val behavior = TemplateBehavior.from(json.optString("behavior"))
                ?: TemplateBehavior.CREATE

            Template(
                name = name,
                noteContentFormat = content,
                behavior = behavior,
                properties = propertiesOf(json.optJSONArray("properties")),
                triggers = stringsOf(json.optJSONArray("triggers")),
                noteNameFormat = json.optString("noteNameFormat").ifBlank { "{{title}}" },
                path = json.optString("path").ifBlank { "Inbox" },
                context = json.optString("context").ifBlank { null },
            )
        }

        private fun propertiesOf(array: JSONArray?): List<TemplateProperty> {
            if (array == null) return emptyList()
            val out = mutableListOf<TemplateProperty>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (name.isEmpty()) continue
                out += TemplateProperty(
                    name = name,
                    value = item.optString("value"),
                    type = PropertyType.from(item.optString("type")) ?: PropertyType.TEXT,
                )
            }
            return out
        }

        private fun stringsOf(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            val out = mutableListOf<String>()
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf(String::isNotEmpty)?.let(out::add)
            }
            return out
        }
    }
}

data class TemplateProperty(
    val name: String,
    val value: String,
    val type: PropertyType = PropertyType.TEXT,
)

enum class TemplateBehavior(val wire: String) {
    CREATE("create"),
    APPEND_SPECIFIC("append-specific"),
    APPEND_DAILY("append-daily"),
    PREPEND_SPECIFIC("prepend-specific"),
    PREPEND_DAILY("prepend-daily"),
    OVERWRITE("overwrite");

    companion object {
        fun from(value: String?): TemplateBehavior? = values().firstOrNull { it.wire == value }
    }
}

enum class PropertyType(val wire: String) {
    TEXT("text"),
    NUMBER("number"),
    CHECKBOX("checkbox"),
    DATE("date"),
    DATETIME("datetime"),
    MULTITEXT("multitext");

    companion object {
        fun from(value: String?): PropertyType? = values().firstOrNull { it.wire == value }
    }
}

/**
 * The templates that ship with the app, in the clipper's language.
 *
 * They are seeded into `Wisp/templates/` on first use, so the user can edit a real file instead of a
 * hidden string, and an exported file imports into the clipper unchanged.
 *
 * The first template in the list is the fallback, as it is in the clipper: a URL that matches no
 * trigger uses it.
 */
object Templates {
    val article = Template(
        name = "Article",
        noteContentFormat = """
            {% if description %}> {{description}}

            {% endif %}{{content}}
        """.trimIndent(),
        properties = listOf(
            TemplateProperty("author", "{{author}}"),
            TemplateProperty("site", "{{site}}"),
            TemplateProperty("published", "{{published}}", PropertyType.DATE),
            TemplateProperty("created", "{{date}}", PropertyType.DATE),
            TemplateProperty("tags", "clippings", PropertyType.MULTITEXT),
        ),
        triggers = emptyList(),
        noteNameFormat = "{{title}}",
        path = "Inbox",
    )

    val reel = Template(
        name = "Instagram reel",
        noteContentFormat = """
            {% if author %}**@{{author}}**

            {% endif %}### Summary

            {{content}}
            {% if linksMd %}
            ### Links

            {{linksMd}}
            {% endif %}{% if chatUrl %}
            [Full Meta AI chat]({{chatUrl}})
            {% endif %}
        """.trimIndent(),
        properties = listOf(
            TemplateProperty("author", "{{author}}"),
            TemplateProperty("created", "{{date}}", PropertyType.DATE),
            TemplateProperty("tags", "reel", PropertyType.MULTITEXT),
            TemplateProperty("chat", "{{chatUrl}}"),
            TemplateProperty("image", "{{image}}"),
        ),
        triggers = listOf(
            "https://www.instagram.com/reel/",
            "https://www.instagram.com/reels/",
            "https://www.instagram.com/p/",
        ),
        noteNameFormat = "{{title}}",
        path = "Inbox",
    )

    /** Order is priority: [article] is first, so it is the fallback. */
    val builtIn: List<Template> = listOf(article, reel)
}
