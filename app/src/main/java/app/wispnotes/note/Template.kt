package app.wispnotes.note

import org.json.JSONArray
import org.json.JSONObject

/**
 * An Obsidian Web Clipper template, read from JSON.
 *
 * The format is the clipper's own export shape (`schemaVersion 0.1.0`), so a template file moves
 * between the two apps with no converter. `id` is not part of the format: the file name is the id.
 *
 * One deliberate difference: the clipper's `noteContentFormat` is the whole note body. Here the app
 * owns the file (`## My notes`, `## Transcript`, `## AI`, frontmatter), so a template's body is
 * written inside the app-owned `## Text` section. Everything else — variables, filters,
 * conditionals, loops, properties, triggers — behaves the same.
 *
 * [path] is a folder name, not a path: notes go to one folder, and the library reads it back.
 */
data class Template(
    val name: String,
    val noteContentFormat: String,
    val behavior: TemplateBehavior = TemplateBehavior.CREATE,
    val properties: List<TemplateProperty> = emptyList(),
    val triggers: List<String> = emptyList(),
    val noteNameFormat: String = "{{title}}",
    val path: String = TemplateFolders.DEFAULT,
    val context: String? = null,
) {
    /** A file name for this template, used when it is written into the vault. */
    fun fileName(): String = Note.slug(name) + ".json"

    /** True when the template cannot be rendered before the page is read. */
    /**
     * True when the template reads the page, so the save must wait for it.
     *
     * `{{transcript}}` is not here on purpose: a transcript is the app's own section, filled by the
     * worker because the WebView path can take far longer than a share should wait. A template never
     * places it.
     */
    fun needsPage(): Boolean = noteContentFormat.contains("{{content}}")

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

            // Only `create` is supported, and only a folder name: the app writes one note, once.
            val wire = json.optString("behavior").trim()
            val behavior = if (wire.isEmpty()) TemplateBehavior.CREATE else TemplateBehavior.from(wire)
            require(behavior == TemplateBehavior.CREATE) { "Only behavior \"create\" is supported." }

            val path = json.optString("path").trim().ifBlank { TemplateFolders.DEFAULT }
            require(!path.contains('/')) { "A template folder is one name, with no slashes." }
            require(!path.contains("..")) { "A template folder cannot point above the vault." }
            require(!path.startsWith(".")) { "A template folder cannot start with a dot." }

            Template(
                name = name,
                noteContentFormat = content,
                behavior = behavior,
                properties = propertiesOf(json.optJSONArray("properties")),
                triggers = stringsOf(json.optJSONArray("triggers")),
                noteNameFormat = json.optString("noteNameFormat").ifBlank { "{{title}}" },
                path = path,
                context = json.optString("context").ifBlank { null },
            )
        }

        /**
         * The template seeded into `templates/default.json` once, at setup.
         *
         * Not a runtime fallback: a vault that has template files is never seeded, and a URL that
         * matches nothing is offered a template instead of being saved with this one silently.
         */
        fun default(): Template = Template(
            name = "Default",
            noteContentFormat = "{% if description %}> {{description}}\n\n{% endif %}{{content}}",
            properties = listOf(
                TemplateProperty("author", "{{author}}"),
                TemplateProperty("published", "{{published}}", PropertyType.DATE),
                TemplateProperty("created", "{{date}}", PropertyType.DATE),
                TemplateProperty("description", "{{description}}", PropertyType.TEXT),
                TemplateProperty("tags", "clippings", PropertyType.MULTITEXT),
            ),
            triggers = emptyList(),
            noteNameFormat = "{{title}}",
            path = TemplateFolders.DEFAULT,
        )

        /** The file name the default template is seeded under. */
        const val DEFAULT_FILE = "default.json"

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
