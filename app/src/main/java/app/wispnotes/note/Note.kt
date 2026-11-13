package app.wispnotes.note

import java.time.LocalDate

/** Stub note written on share. Frontmatter keys are stable; see plan.md section 6. */
data class NoteDraft(
    val info: SourceInfo,
    val url: String,
    val title: String,
    val saved: LocalDate,
    /** Pasted by the user when extraction cannot read the page (Instagram). Goes under `## Text`. */
    val caption: String = "",
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

    fun render(d: NoteDraft): String = buildString {
        appendLine("---")
        appendLine("type: ${d.info.type}")
        appendLine("source: ${d.info.source}")
        appendLine("url: ${Frontmatter.quote(d.url)}")
        appendLine("title: ${Frontmatter.quote(d.title)}")
        appendLine("saved: ${d.saved}")
        appendLine("status: queued")
        appendLine("est_minutes:")
        appendLine("rating:")
        appendLine("tags: [${d.info.tags.joinToString(", ")}]")
        appendLine("extracted: pending")
        appendLine("transcript: pending")
        appendLine("---")
        appendLine()
        appendLine("## My notes")
        appendLine()
        if (d.caption.isNotBlank()) append(Sections.textBlock(d.caption))
    }
}
