package app.wispnotes.note

/** A link meta.ai gave back, already decoded out of its `l.meta.ai` redirect. */
data class AiLink(val text: String, val url: String)

/**
 * The body sections. `## Text` is the app's (written once, only while empty). `## My notes` belongs to the
 * user: the app replaces its content only when the user edits it, and touches nothing outside it.
 */
object Sections {
    private const val HEADING = "## Text"
    private const val TRANSCRIPT = "## Transcript"
    private const val MINE = "## My notes"
    private const val AI = "## AI"

    // The user's section ends at the next heading the app itself writes. Any other `## ` line is the
    // user's own heading and stays inside their notes, so an edit can never duplicate or cut their text.
    private val APP_HEADINGS = setOf(HEADING, TRANSCRIPT, AI)

    fun textBlock(text: String) = "$HEADING\n\n${text.trim()}\n"

    /**
     * Writes only the app-owned `## AI` section, replacing it if it is already there: reading the same
     * note twice must not stack two answers. [links] are the addresses meta.ai hid behind `l.meta.ai`
     * redirects, so they are written out as Markdown; the answer text alone loses them.
     */
    fun withAi(content: String, answer: String, links: List<AiLink>, chatUrl: String?): String {
        val text = answer.trim()
        if (text.isEmpty()) return content
        val block = buildString {
            appendLine(AI)
            appendLine()
            appendLine(text)
            if (links.isNotEmpty()) {
                appendLine()
                appendLine("### Links")
                appendLine()
                links.forEach { link ->
                    val label = link.text.ifBlank { link.url }.replace("[", "\\[").replace("]", "\\]")
                    appendLine("- [$label](${link.url})")
                }
            }
            if (!chatUrl.isNullOrBlank()) {
                appendLine()
                appendLine("_[Chat with Meta AI]($chatUrl)_")
            }
        }.trimEnd('\n')
        return replaceSection(content, AI, block)
    }

    /** The `## AI` section's text, or null when the note has none yet. */
    fun ai(content: String): String? {
        val lines = content.split("\n")
        val at = lines.indexOfFirst { it.trimEnd() == AI }
        if (at < 0) return null
        val next = (at + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
        return lines.subList(at + 1, next).joinToString("\n").trim().ifEmpty { null }
    }

    /** Replaces the section starting at [heading] in place, or appends it. Touches nothing else. */
    private fun replaceSection(content: String, heading: String, block: String): String {
        val raw = content.split("\n")
        val at = raw.indexOfFirst { it.trimEnd() == heading }
        if (at < 0) return content.trimEnd('\n') + "\n\n" + block + "\n"
        val next = (at + 1 until raw.size).firstOrNull { raw[it].startsWith("## ") } ?: raw.size
        return (raw.subList(0, at) + block.split("\n") + raw.subList(next, raw.size)).joinToString("\n")
    }

    /** Writes only the app-owned transcript section. */
    fun withTranscript(content: String, lines: List<TranscriptLine>, url: String): String {
        if (lines.isEmpty()) return content
        val block = buildString {
            appendLine(TRANSCRIPT)
            appendLine()
            lines.forEach { line ->
                Source.youtubeTimestampUrl(url, line.startSeconds)?.let { link ->
                    appendLine("**[${formatTranscriptTimestamp(line.startSeconds)}]($link)** ${line.text}")
                }
            }
        }.trimEnd('\n')
        return replaceSection(content, TRANSCRIPT, block)
    }

    fun transcript(content: String): List<TranscriptLine> =
        TranscriptParser.parseMarkdown(Frontmatter.body(content).substringAfter("$TRANSCRIPT\n", "").substringBefore("\n## "))

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

    /** The note body to show as Markdown: no frontmatter, `## My notes`, or app transcript. */
    fun readable(content: String): String {
        val body = Frontmatter.body(content).split("\n")
        val withoutMine = mine(body)?.let { r -> body.subList(0, r.first) + body.subList(r.last + 1, body.size) } ?: body
        val transcriptAt = withoutMine.indexOfFirst { it.trimEnd() == TRANSCRIPT }
        val withoutTranscript = if (transcriptAt < 0) withoutMine else {
            val next = (transcriptAt + 1 until withoutMine.size).firstOrNull { withoutMine[it].startsWith("## ") } ?: withoutMine.size
            withoutMine.subList(0, transcriptAt) + withoutMine.subList(next, withoutMine.size)
        }
        return withoutTranscript.joinToString("\n").trim()
    }
}
