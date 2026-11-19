package app.wispnotes.note

import app.wispnotes.extract.Markdown
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import org.jsoup.Jsoup

/** A template filter: the current value, the raw argument from the template, and the new value. */
typealias Filter = (Any?, String?) -> Any?

/**
 * The filters clipper templates use, on the JVM.
 *
 * Knap ships these in JavaScript; there is no JS engine in the app, so each one is a small pure
 * function. Names and arguments match the clipper's, including `replace:"old":"new"`, so a template
 * that works there works here. An unknown filter is a no-op, and a filter that throws returns its
 * input: a bad template must not lose a saved post.
 */
object TemplateFilters {
    /** How a value reads inside a sentence. Numbers lose the trailing `.0`. */
    fun text(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is Double -> if (value.isFinite() && value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        is Float -> if (value.isFinite() && value % 1f == 0f) value.toLong().toString() else value.toString()
        is Number -> value.toString()
        is Boolean -> value.toString()
        is Collection<*> -> value.joinToString(", ") { text(it) }
        is Array<*> -> value.joinToString(", ") { text(it) }
        is Map<*, *> -> value.entries.joinToString(", ") { "${it.key}: ${text(it.value)}" }
        else -> value.toString()
    }

    /** The argument with one layer of quotes removed. `"old":"new"` keeps its inner quotes. */
    private fun arg(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.length < 2) return raw.ifEmpty { null }
        val first = raw.first()
        val last = raw.last()
        return if ((first == '"' || first == '\'') && last == first) raw.substring(1, raw.length - 1) else raw
    }

    private fun asList(value: Any?): List<Any?> = when (value) {
        null -> emptyList()
        is List<*> -> value.toList()
        is Collection<*> -> value.toList()
        is Array<*> -> value.toList()
        else -> listOf(value)
    }

    private val upper: Filter = { value, _ -> text(value).uppercase() }
    private val lower: Filter = { value, _ -> text(value).lowercase() }
    private val trim: Filter = { value, _ -> text(value).trim() }
    private val safe: Filter = { value, _ -> value }

    private val capitalize: Filter = { value, _ ->
        text(value).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
    }

    private val titleCase: Filter = { value, _ ->
        text(value).split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
        }
    }

    /** `replace:"old":"new"` — the argument is one string with a colon between the two values. */
    private val replace: Filter = { value, argument ->
        val raw = arg(argument).orEmpty()
        val parts = if (raw.contains("\":\"")) raw.split("\":\"") else raw.split(",").map(String::trim)
        if (parts.size == 2) text(value).replace(parts[0], parts[1]) else text(value)
    }

    /** `slice:0,100` — start and end, end exclusive, as in Kotlin. */
    private val slice: Filter = { value, argument ->
        val parts = arg(argument).orEmpty().split(",").map(String::trim)
        val from = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val to = parts.getOrNull(1)?.toIntOrNull()
        val source = text(value)
        val start = from.coerceIn(0, source.length)
        val end = (to ?: source.length).coerceIn(start, source.length)
        source.substring(start, end)
    }

    private val default: Filter = { value, argument ->
        val empty = value == null || (value is String && value.isBlank()) ||
            (value is Collection<*> && value.isEmpty())
        if (empty) arg(argument).orEmpty() else value
    }

    private val join: Filter = { value, argument -> asList(value).joinToString(arg(argument) ?: ", ") { text(it) } }
    private val split: Filter = { value, argument -> text(value).split(arg(argument) ?: " ") }
    private val first: Filter = { value, _ -> asList(value).firstOrNull() }
    private val last: Filter = { value, _ -> asList(value).lastOrNull() }

    private val length: Filter = { value, _ ->
        when (value) {
            null -> 0
            is String -> value.length
            is Collection<*> -> value.size
            is Map<*, *> -> value.size
            else -> text(value).length
        }
    }

    private val list: Filter = { value, _ ->
        asList(value).joinToString("\n") { "- ${text(it)}" }
    }

    private val link: Filter = { value, argument ->
        val url = text(value)
        val label = arg(argument) ?: url
        if (url.isBlank()) label else "[$label]($url)"
    }

    private val image: Filter = { value, argument ->
        val url = text(value)
        val alt = arg(argument).orEmpty()
        if (url.isBlank()) "" else "![$alt]($url)"
    }

    private val wikilink: Filter = { value, argument ->
        val note = text(value)
        val alias = arg(argument)
        if (alias == null) "[[$note]]" else "[[$note|$alias]]"
    }

    private val blockquote: Filter = { value, _ ->
        text(value).lines().filter(String::isNotBlank).joinToString("\n") { "> $it" }
    }

    /** `callout:"note"` */
    private val callout: Filter = { value, argument ->
        val kind = arg(argument) ?: "note"
        val body = text(value).lines().filter(String::isNotBlank).joinToString("\n") { "> $it" }
        "> [!$kind]\n$body"
    }

    private val stripTags: Filter = { value, _ -> Jsoup.parse(text(value)).text() }

    /** HTML to Markdown, for a `selectorHtml:` style value. */
    private val markdown: Filter = { value, _ ->
        val html = text(value)
        if (html.isBlank()) "" else Markdown.from(Jsoup.parse(html).body()).orEmpty()
    }

    private val date: Filter = { value, argument ->
        val parsed = parseDate(text(value))
        if (parsed == null) text(value) else formatDate(parsed, arg(argument) ?: "YYYY-MM-DD")
    }

    val all: Map<String, Filter> = mapOf(
        "upper" to upper,
        "lower" to lower,
        "trim" to trim,
        "safe" to safe,
        "capitalize" to capitalize,
        "title" to titleCase,
        "replace" to replace,
        "slice" to slice,
        "default" to default,
        "join" to join,
        "split" to split,
        "first" to first,
        "last" to last,
        "length" to length,
        "list" to list,
        "link" to link,
        "image" to image,
        "wikilink" to wikilink,
        "blockquote" to blockquote,
        "callout" to callout,
        "strip_tags" to stripTags,
        "remove_html" to stripTags,
        "markdown" to markdown,
        "date" to date,
    )

    /* ------------------------------------------------------------------ time */

    private fun parseDate(value: String): LocalDateTime? {
        val raw = value.trim()
        if (raw.isEmpty()) return null
        runCatching { OffsetDateTime.parse(raw) }.getOrNull()?.let { return it.toLocalDateTime() }
        runCatching { Instant.parse(raw) }.getOrNull()?.let { return LocalDateTime.ofInstant(it, ZoneOffset.UTC) }
        runCatching { LocalDateTime.parse(raw) }.getOrNull()?.let { return it }
        runCatching { LocalDate.parse(raw) }.getOrNull()?.let { return it.atStartOfDay() }
        return null
    }

    private val DATE_TOKENS = Regex("YYYY|YY|MMMM|MMM|MM|M|DD|D|HH|H|mm|m|ss|s")

    /** Supports the tokens a clipper template uses: YYYY, MM, DD, HH, mm and their short forms. */
    private fun formatDate(date: LocalDateTime, pattern: String): String = DATE_TOKENS.replace(pattern) { match ->
        when (match.value) {
            "YYYY" -> date.year.toString().padStart(4, '0')
            "YY" -> (date.year % 100).toString().padStart(2, '0')
            "MMMM" -> date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            "MMM" -> date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            "MM" -> date.monthValue.toString().padStart(2, '0')
            "M" -> date.monthValue.toString()
            "DD" -> date.dayOfMonth.toString().padStart(2, '0')
            "D" -> date.dayOfMonth.toString()
            "HH" -> date.hour.toString().padStart(2, '0')
            "H" -> date.hour.toString()
            "mm" -> date.minute.toString().padStart(2, '0')
            "m" -> date.minute.toString()
            "ss" -> date.second.toString().padStart(2, '0')
            "s" -> date.second.toString()
            else -> match.value
        }
    }
}
