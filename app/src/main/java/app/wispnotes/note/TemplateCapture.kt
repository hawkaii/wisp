package app.wispnotes.note

import app.wispnotes.extract.Extracted
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Everything a template can read, gathered from whichever half of the save knows it.
 *
 * At share time the draft knows the title, URL and date; the extraction knows the body, author and
 * publisher; the reader later knows the AI answer. One type covers all three so a template is
 * rendered the same way wherever it runs.
 */
data class Capture(
    val url: String = "",
    val title: String = "",
    val author: String? = null,
    val site: String? = null,
    val description: String? = null,
    val published: String? = null,
    val image: String? = null,
    val content: String = "",
    val date: LocalDate = LocalDate.now(),
    val type: String = "article",
    val source: String = "web",
    val minutes: Int? = null,
    val links: List<AiLink> = emptyList(),
    val chatUrl: String? = null,
    val selection: String = "",
    /** What the user shared beside the link. Its own variable: it is not the page's text. */
    val caption: String = "",
    val tags: List<String> = emptyList(),
) {
    val domain: String
        get() = runCatching { URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")

    fun variables(): Map<String, Any?> = linkedMapOf(
        "title" to title,
        "url" to url,
        "author" to (author ?: ""),
        "site" to (site ?: ""),
        "domain" to domain,
        "description" to (description ?: ""),
        "published" to (published ?: ""),
        "image" to (image ?: ""),
        "content" to content,
        "date" to date.toString(),
        "time" to date.toString(),
        "words" to content.split(Regex("\\s+")).count(String::isNotBlank),
        "noteName" to Note.slug(title),
        "type" to type,
        "source" to source,
        "minutes" to (minutes ?: 0),
        "selection" to selection,
        "caption" to caption,
        "tags" to tags,
        "links" to links.map { mapOf("text" to it.text, "href" to it.url) },
        "linksMd" to links.joinToString("\n") { "- [${it.text.ifBlank { it.url }}](${it.url})" },
        "chatUrl" to (chatUrl ?: ""),
    )

    companion object {
        /** The metadata a share already knows, before the page is fetched. */
        fun of(draft: NoteDraft): Capture = Capture(
            url = draft.url,
            title = draft.title,
            date = draft.saved,
            type = draft.info.type,
            source = draft.info.source,
            // A caption is not the page text: it has its own variable and content waits for the page.
            caption = draft.caption,
            content = "",
            tags = draft.info.tags,
        )

        /** The draft plus what the page already gave, for a template rendered at save time. */
        fun of(draft: NoteDraft, found: Extracted?): Capture {
            val base = of(draft)
            if (found == null || found.status == "failed") return base
            return base.copy(
                title = found.title ?: base.title,
                author = found.author,
                site = found.site,
                description = found.description,
                published = found.published,
                image = found.thumbnail,
                content = found.text.orEmpty(),
                minutes = found.minutes,
            )
        }

        /** The note as it stands plus what the extractor found. */
        fun of(content: String, found: Extracted, url: String): Capture = Capture(
            url = url,
            title = found.title ?: Frontmatter.text(content, "title").orEmpty(),
            author = found.author,
            site = found.site,
            description = found.description,
            published = found.published,
            image = found.thumbnail,
            content = found.text.orEmpty(),
            date = Frontmatter.text(content, "saved")?.let(::parseDate) ?: LocalDate.now(),
            type = Frontmatter.text(content, "type") ?: "article",
            source = Frontmatter.text(content, "source") ?: "web",
            minutes = found.minutes,
            tags = Frontmatter.tags(content),
        )

        private fun parseDate(value: String): LocalDate? {
            val raw = value.trim()
            runCatching { LocalDate.parse(raw) }.getOrNull()?.let { return it }
            runCatching { OffsetDateTime.parse(raw) }.getOrNull()?.let { return it.toLocalDate() }
            runCatching { Instant.parse(raw) }.getOrNull()?.let { return it.atOffset(java.time.ZoneOffset.UTC).toLocalDate() }
            return null
        }
    }
}

/** One template property, rendered but not yet formatted as YAML. */
data class RenderedProperty(val name: String, val value: String, val type: PropertyType)

/**
 * Renders a template into the parts the app writes: a note name, YAML values, and the body of the
 * app-owned `## Text` section.
 *
 * Why the body and not the whole file: the clipper's `noteContentFormat` is a whole note. Here the
 * app owns the frontmatter and the `## ` headings (`My notes`, `Transcript`, `AI`); they are the
 * contract with the index and with the user's edits. A template's own `## ` headings are therefore
 * moved one level down, so they cannot be mistaken for an app section.
 */
object TemplateRender {
    fun noteName(template: Template, capture: Capture): String =
        TemplateEngine.render(template.noteNameFormat, capture.variables()).trim().ifBlank { capture.title }

    fun rendered(template: Template, capture: Capture): List<RenderedProperty> {
        val variables = capture.variables()
        return template.properties.map { property ->
            RenderedProperty(property.name, TemplateEngine.render(property.value, variables), property.type)
        }
    }

    /** YAML for one property, or null when its value is empty: an empty key is noise in a vault. */
    fun yaml(property: RenderedProperty): String? {
        val value = property.value.trim()
        if (value.isEmpty()) return null
        return when (property.type) {
            PropertyType.TEXT -> Frontmatter.quote(value)
            PropertyType.NUMBER -> if (NUMBER.matches(value)) value else Frontmatter.quote(value)
            PropertyType.CHECKBOX ->
                if (value.equals("false", true) || value.equals("no", true) || value == "0") "false" else "true"

            PropertyType.DATE -> DAY.find(value)?.value ?: Frontmatter.quote(value)
            PropertyType.DATETIME -> timestamp(value) ?: Frontmatter.quote(value)
            PropertyType.MULTITEXT ->
                Frontmatter.yamlList(value.split(",").map(String::trim).filter(String::isNotEmpty))
        }
    }

    /** Every property the app does not reserve, as `name to yaml`, skipping empty values. */
    fun properties(template: Template, capture: Capture): Map<String, String> =
        rendered(template, capture)
            .filter { it.name !in Template.RESERVED && it.name != TAGS }
            .mapNotNull { property -> yaml(property)?.let { property.name to it } }
            .toMap()

    /** Tags a template asks for. The app adds its own and never drops the user's. */
    fun tags(template: Template, capture: Capture): List<String> =
        rendered(template, capture)
            .filter { it.name == TAGS }
            .flatMap { it.value.split(",") }
            .map(String::trim)
            .filter(String::isNotEmpty)

    /** The `## Text` body. Null when the template renders nothing. */
    fun body(template: Template, capture: Capture): String? {
        val out = TemplateEngine.render(template.noteContentFormat, capture.variables()).trim()
        return if (out.isEmpty()) null else demote(out)
    }

    private const val TAGS = "tags"
    private val NUMBER = Regex("-?\\d+(\\.\\d+)?")
    private val DAY = Regex("\\d{4}-\\d{2}-\\d{2}")

    private fun timestamp(value: String): String? =
        runCatching { OffsetDateTime.parse(value).toString() }.getOrNull()
            ?: runCatching { Instant.parse(value).toString() }.getOrNull()

    private fun demote(markdown: String): String =
        markdown.lines().joinToString("\n") { line -> if (line.startsWith("## ")) "#$line" else line }
}

/** Picks a template for a URL, the same way the clipper does. */
object TemplatePick {
    /** The first template whose trigger matches, or null when none does. No silent fallback. */
    fun pick(templates: List<Template>, url: String, schemaTypes: List<String> = emptyList()): Template? =
        templates.firstOrNull { template ->
            template.triggers.any { matches(it, url, schemaTypes) }
        }

    /** `https://…` is a prefix, `/regex/` is a pattern, `schema:@Type` reads the page's JSON-LD. */
    fun matches(trigger: String, url: String, schemaTypes: List<String> = emptyList()): Boolean {
        val rule = trigger.trim()
        if (rule.isEmpty() || url.isEmpty()) return false

        if (rule.startsWith("schema:")) {
            val wanted = rule.removePrefix("schema:").substringBefore("=").removePrefix("@").lowercase()
            return schemaTypes.any { it.removePrefix("@").lowercase() == wanted }
        }

        if (rule.length > 2 && rule.startsWith("/") && rule.endsWith("/")) {
            return runCatching { Regex(rule.substring(1, rule.length - 1)).containsMatchIn(url) }
                .getOrDefault(false)
        }

        // A hand-written or imported trigger may omit `www.`; the shared link usually has it. Compare
        // the two on equal footing so a template is not dead because of three characters.
        return stripWww(url).startsWith(stripWww(rule))
    }

    private fun stripWww(value: String): String =
        value.replaceFirst("https://www.", "https://").replaceFirst("http://www.", "http://")
}
