package app.wispnotes.note

/** The one body section the app owns: `## Text`. `## My notes` is never read or changed here. */
object Sections {
    private const val HEADING = "## Text"

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
}
