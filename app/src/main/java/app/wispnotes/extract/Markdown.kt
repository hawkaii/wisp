package app.wispnotes.extract

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * HTML to Markdown, for the body of a saved page.
 *
 * The note is a Markdown file in a vault, so the body has to be Markdown. A readability pass that
 * keeps only paragraph text throws away headings, lists, quotes, code and the address of every link;
 * that is why a clipped page used to read as a wall of paragraphs.
 *
 * Jsoup is already a dependency and the tree is already parsed, so this walks it directly. It is
 * deliberately a hand-written tree walk and not a regex pass: nesting is what breaks regex HTML.
 *
 * Images stay as remote links. Inlining every picture would bloat the vault, and the one expiring
 * case the app cares about (Instagram's signed CDN link) is handled by Images/ExtractWorker.
 */
object Markdown {
    private const val MAX = 50_000

    /** Tags whose content never belongs in a note. Nav, header and footer are removed by the
     *  extractor before this runs; they are repeated here so a direct call stays safe. */
    private val SKIP = setOf(
        "script", "style", "noscript", "template", "svg", "math",
        "form", "input", "button", "select", "textarea", "label",
        "iframe", "canvas", "video", "audio", "source", "track", "object", "embed",
        "nav", "footer",
    )

    private val PARAGRAPH = setOf(
        "div", "section", "article", "main", "figure", "figcaption",
        "details", "summary", "dl", "dt", "dd", "center",
    )

    /** Markdown for [root], or null when it holds no text. Capped: one huge page must not fill the vault. */
    fun from(root: Element?): String? {
        if (root == null) return null
        return tidy(render(root.childNodes())).take(MAX).ifBlank { null }
    }

    /** Markdown for a list of sibling nodes. */
    private fun render(nodes: List<Node>): String = nodes.joinToString("") { node(it) }

    private fun node(n: Node): String = when (n) {
        // HTML source newlines are layout, not meaning: a paragraph is one line. `<br>`, the
        // elements around this text and the block rules below are what make line breaks.
        is TextNode -> n.wholeText.replace(Regex("\\s+"), " ")
        is Element -> element(n)
        else -> "" // comments, data nodes, doctypes
    }

    private fun element(el: Element): String {
        val tag = el.tagName().lowercase()
        if (tag in SKIP) return ""

        return when (tag) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> heading(el, tag)
            "br" -> "\n"
            "hr" -> "\n\n---\n\n"
            "p" -> paragraph(inner(el))
            "strong", "b" -> wrap(inner(el), "**")
            "em", "i" -> wrap(inner(el), "*")
            "del", "s", "strike" -> wrap(inner(el), "~~")
            "u", "mark", "small", "span", "abbr", "time", "cite", "q" -> inner(el)
            "code" -> if (el.parent()?.tagName() == "pre") inner(el) else codeSpan(inner(el))
            "pre" -> codeBlock(el)
            "blockquote" -> quote(inner(el))
            "ul" -> list(el, ordered = false)
            "ol" -> list(el, ordered = true)
            "li" -> paragraph(inner(el)) // only reached for a stray li
            "a" -> link(el, inner(el))
            "img" -> image(el)
            "table" -> table(el)
            "sup", "sub" -> inner(el)
            else -> if (tag in PARAGRAPH) paragraph(inner(el)) else inner(el)
        }
    }

    private fun inner(el: Element) = render(el.childNodes())

    private fun heading(el: Element, tag: String): String {
        val level = tag.drop(1).toIntOrNull()?.coerceIn(1, 6) ?: 1
        val text = oneLine(inner(el))
        if (text.isEmpty()) return ""
        return "\n\n" + "#".repeat(level) + " " + text + "\n\n"
    }

    private fun paragraph(text: String): String {
        val clean = text.trim()
        return if (clean.isEmpty()) "" else "\n\n$clean\n\n"
    }

    private fun wrap(text: String, mark: String): String {
        val clean = oneLine(text)
        return if (clean.isEmpty()) "" else mark + clean + mark
    }

    private fun codeSpan(text: String): String {
        val clean = oneLine(text)
        if (clean.isEmpty()) return ""
        return "`" + clean.replace("`", "'") + "`"
    }

    private fun codeBlock(el: Element): String {
        val code = el.wholeText().replace("\r\n", "\n").trim('\n')
        if (code.isBlank()) return ""
        return "\n\n```\n" + code + "\n```\n\n"
    }

    private fun quote(text: String): String {
        val lines = text.trim().lines().map(String::trim).filter(String::isNotEmpty)
        if (lines.isEmpty()) return ""
        return "\n\n" + lines.joinToString("\n") { "> $it" } + "\n\n"
    }

    private fun list(el: Element, ordered: Boolean): String {
        val items = el.children().filter { it.tagName() == "li" }
        if (items.isEmpty()) return inner(el)
        val body = items.mapIndexed { index, li -> item(li, if (ordered) "${index + 1}. " else "- ") }
        return "\n\n" + body.joinToString("\n") + "\n\n"
    }

    private fun item(li: Element, marker: String): String {
        val own = StringBuilder()
        val nested = StringBuilder()
        for (child in li.childNodes()) {
            if (child is Element && (child.tagName() == "ul" || child.tagName() == "ol")) {
                nested.append(list(child, child.tagName() == "ol"))
            } else {
                own.append(node(child))
            }
        }
        val first = marker + oneLine(own.toString())
        val rest = nested.toString().trim().lines().filter(String::isNotBlank)
        if (rest.isEmpty()) return first
        return (listOf(first) + rest.map { "  $it" }).joinToString("\n")
    }

    private fun link(el: Element, label: String): String {
        val raw = el.attr("href").trim()
        if (raw.isEmpty()) return oneLine(label)
        if (raw.startsWith("#")) return oneLine(label) // an in-page anchor is noise in a note
        val href = el.absUrl("href").ifEmpty { raw }
        val text = oneLine(label).ifEmpty { href }
        return "[" + text.replace("[", "\\[").replace("]", "\\]") + "](" + href.replace(" ", "%20") + ")"
    }

    private fun image(el: Element): String {
        val src = el.absUrl("src").ifEmpty { el.attr("src").trim() }
        if (src.isEmpty() || src.startsWith("data:")) return ""
        val alt = el.attr("alt").replace("[", "").replace("]", "")
        return "![$alt]($src)"
    }

    private fun table(el: Element): String {
        val rows = el.select("tr").map { row ->
            row.children()
                .filter { it.tagName() == "td" || it.tagName() == "th" }
                .map { cell -> oneLine(inner(cell)).replace("|", "\\|") }
        }.filter { it.isNotEmpty() }
        if (rows.isEmpty()) return ""
        val width = rows.maxOf { it.size }
        fun line(cells: List<String>) = "| " + (cells + List(width - cells.size) { "" }).joinToString(" | ") + " |"
        val out = mutableListOf(line(rows.first()), "| " + List(width) { "---" }.joinToString(" | ") + " |")
        rows.drop(1).forEach { out += line(it) }
        return "\n\n" + out.joinToString("\n") + "\n\n"
    }

    /** Inline content on one line: a heading or list item is not a place for a paragraph break. */
    private fun oneLine(text: String): String =
        tidy(text).lines().map(String::trim).filter(String::isNotEmpty).joinToString(" ")

    /** Collapses the indentation HTML is full of, without touching the indentation Markdown needs
     *  (a nested list) or intentional blank lines. */
    private fun tidy(text: String): String = text
        .replace("\r\n", "\n")
        .replace('\u00a0', ' ')
        .replace(Regex("[ \\t]+\\n"), "\n") // trailing spaces only
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
