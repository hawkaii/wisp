package app.wispnotes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wispnotes.extract.Extractors
import app.wispnotes.extract.OkHttpFetcher
import app.wispnotes.note.Capture
import app.wispnotes.note.PropertyType
import app.wispnotes.note.Template
import app.wispnotes.vault.SafVaultRepository
import app.wispnotes.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class TemplateTab { Form, Json }

/** One editable row of the `properties` array. */
data class PropertyRow(val name: String, val value: String, val type: String)

/**
 * The form's fields.
 *
 * It is a mirror, not the storage: [TemplateEditorViewModel.base] keeps the parsed JSON, so a key the
 * form does not show (`context`, `vault`, anything the clipper adds later) survives a save. A form-only
 * editor would silently drop it, which is why the JSON tab exists.
 */
data class TemplateForm(
    val name: String = "",
    val triggers: String = "",
    val folder: String = "Clippings",
    val noteName: String = "{{title}}",
    val content: String = "",
    val properties: List<PropertyRow> = emptyList(),
)

/** A variable the link actually provides, so a template is never built around an empty value. */
data class Variable(val name: String, val value: String) {
    val filled: Boolean get() = value.isNotBlank()
}

class TemplateEditorViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: VaultRepository = SafVaultRepository(app)
    private val base = JSONObject()

    /** The file being edited, or null until the first save. */
    val fileName = MutableStateFlow<String?>(null)
    val form = MutableStateFlow(TemplateForm())
    val json = MutableStateFlow("")
    val tab = MutableStateFlow(TemplateTab.Form)
    val error = MutableStateFlow<String?>(null)
    val variables = MutableStateFlow<List<Variable>?>(null)
    val reading = MutableStateFlow(false)
    val saved = MutableStateFlow(false)

    fun open(file: String?, url: String, caption: String) = viewModelScope.launch {
        fileName.value = file
        val text = file?.let { repo.readTemplates().firstOrNull { pair -> pair.first == it }?.second }
        val seed = text ?: newTemplateJson(url)
        json.value = seed
        load(seed)
        if (url.isNotBlank()) inspect(url, caption)
    }

    /** Reads the form from the JSON. A broken file leaves the editor on the JSON tab with the reason. */
    fun load(text: String) {
        val parsed = runCatching { JSONObject(text) }
        parsed.onFailure {
            error.value = "Not valid JSON: ${it.message}"
            tab.value = TemplateTab.Json
            return
        }
        val object0 = parsed.getOrThrow()
        base.keys().forEach { base.remove(it) }
        object0.keys().forEach { key -> base.put(key, object0.get(key)) }
        form.value = formOf(object0)
        error.value = null
    }

    fun edit(next: TemplateForm) {
        form.value = next
        base.put("name", next.name.trim())
        base.put("triggers", JSONArray(next.triggers.lines().map(String::trim).filter(String::isNotEmpty)))
        base.put("path", next.folder.trim())
        base.put("noteNameFormat", next.noteName)
        base.put("noteContentFormat", next.content)
        base.put(
            "properties",
            JSONArray().apply {
                next.properties.filter { it.name.isNotBlank() }.forEach { row ->
                    put(JSONObject().apply {
                        put("name", row.name.trim())
                        put("value", row.value)
                        put("type", row.type)
                    })
                }
            },
        )
        json.value = base.toString(2)
    }

    /** A JSON edit is re-read into the form, so the two tabs never disagree. */
    fun editJson(text: String) {
        json.value = text
        load(text)
    }

    fun switchTo(next: TemplateTab) {
        if (next == TemplateTab.Form) load(json.value)
        if (error.value == null) tab.value = next
    }

    /** Fetches the link so the maker can show which variables exist. */
    fun inspect(url: String, caption: String) = viewModelScope.launch {
        if (url.isBlank()) return@launch
        reading.value = true
        variables.value = null
        val found = withContext(Dispatchers.IO) { Extractors.extract(url, OkHttpFetcher::get, OkHttpFetcher::post) }
        val capture = Capture(
            url = url,
            title = found.title.orEmpty(),
            author = found.author,
            site = found.site,
            description = found.description,
            published = found.published,
            image = found.thumbnail,
            content = found.text.orEmpty(),
            caption = caption,
        )
        variables.value = capture.variables().map { (key, value) -> Variable(key, value?.toString().orEmpty()) }
        reading.value = false
    }

    fun save() = viewModelScope.launch {
        error.value = null
        val text = json.value
        val parsed = Template.fromJson(text)
        parsed.onFailure {
            error.value = it.message ?: "This is not a valid template."
            return@launch
        }
        val name = fileName.value ?: parsed.getOrThrow().fileName()
        repo.writeTemplate(name, text)
            .onSuccess {
                fileName.value = it
                saved.value = true
            }
            .onFailure { error.value = it.message ?: "Could not write $name" }
    }

    private fun formOf(json: JSONObject) = TemplateForm(
        name = json.optString("name"),
        triggers = strings(json.optJSONArray("triggers")).joinToString("\n"),
        folder = json.optString("path").ifBlank { "Clippings" },
        noteName = json.optString("noteNameFormat").ifBlank { "{{title}}" },
        content = json.optString("noteContentFormat"),
        properties = properties(json.optJSONArray("properties")),
    )

    private fun properties(array: JSONArray?): List<PropertyRow> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            PropertyRow(
                name = item.optString("name"),
                value = item.optString("value"),
                type = item.optString("type").ifBlank { PropertyType.TEXT.wire },
            )
        }
    }

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
    }

    /**
     * The file for a template started from a link: the trigger is the site, so the maker opens with
     * something that will match the next share instead of an empty rule.
     */
    private fun newTemplateJson(url: String): String {
        val host = runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
        val trigger = if (host.isEmpty()) "" else "https://$host/"
        val name = host.substringBefore('.').replaceFirstChar { it.uppercase() }.ifEmpty { "New template" }
        return JSONObject().apply {
            put("schemaVersion", Template.SCHEMA_VERSION)
            put("name", name)
            put("behavior", "create")
            put("noteContentFormat", "{{content}}")
            put("properties", JSONArray())
            put("triggers", JSONArray(if (trigger.isEmpty()) emptyList<String>() else listOf(trigger)))
            put("noteNameFormat", "{{title}}")
            put("path", "Clippings")
        }.toString(2)
    }
}
