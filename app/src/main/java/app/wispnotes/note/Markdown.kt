package app.wispnotes.note

enum class MdKind { H1, H2, H3, Bullet, Quote, Para }

data class MdLine(val kind: MdKind, val text: String)

/** Line-based Markdown: headings, bullets, quotes and paragraphs. Inline `**bold**` is handled by the UI. Blank lines are dropped. */
fun parseMarkdown(text: String): List<MdLine> = text.lines().mapNotNull { raw ->
    val l = raw.trim()
    when {
        l.isEmpty() -> null
        l.startsWith("### ") -> MdLine(MdKind.H3, l.drop(4))
        l.startsWith("## ") -> MdLine(MdKind.H2, l.drop(3))
        l.startsWith("# ") -> MdLine(MdKind.H1, l.drop(2))
        l.startsWith("- ") || l.startsWith("* ") -> MdLine(MdKind.Bullet, l.drop(2))
        l.startsWith("> ") -> MdLine(MdKind.Quote, l.drop(2))
        else -> MdLine(MdKind.Para, l)
    }
}
