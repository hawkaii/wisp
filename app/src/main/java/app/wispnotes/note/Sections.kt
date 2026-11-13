package app.wispnotes.note

/**
 * The body sections. `## Text` is the app's (written once, only while empty). `## My notes` belongs to the
 * user: the app replaces its content only when the user edits it, and touches nothing outside it.
 */
object Sections {
    private const val HEADING = "## Text"
    private const val MINE = "## My notes"

    // The user's section ends at the next heading the app itself writes. Any other `## ` line is the
    // user's own heading and stays inside their notes, so an edit can never duplicate or cut their text.
    private val APP_HEADINGS = setOf(HEADING, "## Transcript")

    fun textBlock(text: String) = "$HEADING\n\n${text.trim()}\n"

    /**
     * Adds [text] under `## Text` at the end of the file. If the section already has content
     * (a pasted caption, an earlier run) it is kept and [content] comes back unchanged.
     */
    fun withText(content: String, text: String): String {
        if (text.isBlank()) return content
        val lines = content.split("\n")
        val at = lines.indexOfFirst { it.trimEnd() == HEADING }
        if (at < 0) return content.trimEnd('\n') + "\n\n" + textBlock(text)
        val next = (at + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
        if (lines.subList(at + 1, next).any { it.isNotBlank() }) return content
        return (lines.subList(0, at) + textBlock(text).trimEnd('\n').split("\n") + listOf("") + lines.subList(next, lines.size))
            .joinToString("\n")
    }

    /** Line range of the heading plus the user's text, or null when there is no `## My notes`. */
    private fun mine(lines: List<String>): IntRange? {
        val at = lines.indexOfFirst { it.trimEnd() == MINE }
        if (at < 0) return null
        val next = (at + 1 until lines.size).firstOrNull { lines[it].trimEnd() in APP_HEADINGS } ?: lines.size
        return at until next
    }

    /** What the user wrote under `## My notes`, trimmed. Empty when the section is missing. */
    fun myNotes(content: String): String {
        val lines = content.split("\n")
        val r = mine(lines) ?: return ""
        return lines.subList(r.first + 1, r.last + 1).joinToString("\n").trim()
    }

    /** Replaces only the user's section. Same text in -> the very same [content] out. */
    fun withMyNotes(content: String, text: String): String {
        val clean = text.trim()
        if (myNotes(content) == clean) return content
        val lines = content.split("\n")
        val r = mine(lines)
        val block = listOf(MINE, "") + (if (clean.isEmpty()) emptyList() else clean.split("\n")) + listOf("")
        if (r == null) return content.trimEnd('\n') + "\n\n" + block.joinToString("\n")
        return (lines.subList(0, r.first) + block + lines.subList(r.last + 1, lines.size)).joinToString("\n")
    }

    /** The note body to show as Markdown: no frontmatter and no `## My notes` (it has its own editor). */
    fun readable(content: String): String {
        val body = Frontmatter.body(content).split("\n")
        val r = mine(body)
        return (if (r == null) body else body.subList(0, r.first) + body.subList(r.last + 1, body.size))
            .joinToString("\n").trim()
    }
}
