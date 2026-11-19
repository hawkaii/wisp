package app.wispnotes.note

import java.time.LocalDate

/** Stub note written on share. Frontmatter keys are stable; see plan.md section 6. */
data class NoteDraft(
    val info: SourceInfo,
    val url: String,
    val title: String,
    val saved: LocalDate,
    /** Pasted by the user when extraction cannot read the page (Instagram). `{{caption}}` for a template. */
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

    fun fileName(d: NoteDraft, template: Template? = null): String {
        val name = template?.let { TemplateRender.noteName(it, Capture.of(d)) } ?: d.title
        return "${d.saved}-${slug(name)}.md"
    }

    /**
     * The note as written at share time. [capture] is what the template can read now: the draft
     * alone, or the draft plus the page when the template needs it. The template body is rendered
     * here, once, so extraction never has to rewrite `## Text`.
     */
    fun render(d: NoteDraft, template: Template? = null, capture: Capture = Capture.of(d)): String = buildString {
        val extraTags = if (template == null) emptyList() else TemplateRender.tags(template, capture)
        val tags = Frontmatter.mergeTags(d.info.tags, extraTags)

        appendLine("---")
        appendLine("type: ${d.info.type}")
        appendLine("source: ${d.info.source}")
        appendLine("url: ${Frontmatter.quote(d.url)}")
        appendLine("title: ${Frontmatter.quote(d.title)}")
        appendLine("saved: ${d.saved}")
        appendLine("status: queued")
        appendLine("est_minutes:")
        appendLine("rating:")
        appendLine("tags: [${tags.joinToString(", ")}]")
        appendLine("extracted: pending")
        appendLine("transcript: pending")
        if (template != null) {
            // The name is how the worker finds the template again when the page is extracted.
            appendLine("wisp_template: ${Frontmatter.quote(template.name)}")
            TemplateRender.properties(template, capture).forEach { (key, value) -> appendLine("$key: $value") }
        }
        appendLine("---")
        appendLine()
        appendLine("## My notes")
        appendLine()
        if (template != null) {
            TemplateRender.body(template, capture)?.let { append(Sections.textBlock(it)) }
        } else if (d.caption.isNotBlank()) {
            append(Sections.textBlock(d.caption))
        }
    }
}
