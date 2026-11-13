package app.wispnotes.note

import java.time.LocalDate

/** Stub note written on share. Frontmatter keys are stable; see plan.md section 6. */
data class NoteDraft(
    val info: SourceInfo,
    val url: String,
    val title: String,
    val saved: LocalDate,
)

object Note {
    fun slug(title: String, max: Int = 50): String =
        title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(max)
            .trim('-')
            .ifEmpty { "note" }

    fun fileName(d: NoteDraft) = "${d.saved}-${slug(d.title)}.md"

    private fun q(s: String) =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace(Regex("[\\r\\n]+"), " ") + "\""

    fun render(d: NoteDraft): String = buildString {
        appendLine("---")
        appendLine("type: ${d.info.type}")
        appendLine("source: ${d.info.source}")
        appendLine("url: ${q(d.url)}")
        appendLine("title: ${q(d.title)}")
        appendLine("saved: ${d.saved}")
        appendLine("status: queued")
        appendLine("est_minutes:")
        appendLine("rating:")
        appendLine("tags: [${d.info.tags.joinToString(", ")}]")
        appendLine("extracted: pending")
        appendLine("---")
        appendLine()
        appendLine("## My notes")
        appendLine()
    }
}
