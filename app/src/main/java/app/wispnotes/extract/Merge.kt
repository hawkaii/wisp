package app.wispnotes.extract

import app.wispnotes.note.Capture
import app.wispnotes.note.Frontmatter
import app.wispnotes.note.Sections
import app.wispnotes.note.Source
import app.wispnotes.note.Template
import app.wispnotes.note.TemplateRender
import java.net.URI

/** Writes an extraction into a note. Touches only keys the app owns; `## My notes` is never read. */
object Merge {
    /**
     * [template] is the template the note was saved with, or null for the plain format. A template
     * adds properties and shapes the `## Text` body; it cannot rewrite a key the app owns, and the
     * user's value always wins over a rendered one.
     */
    fun apply(content: String, e: Extracted, template: Template? = null, status: String = e.status): String {
        val url = Frontmatter.text(content, "url").orEmpty()
        val set = linkedMapOf<String, String>()
        val capture = template?.let { Capture.of(content, e, url) }

        if (e.title != null && isPlaceholder(Frontmatter.text(content, "title"), url)) set["title"] = Frontmatter.quote(e.title)
        // A value the user already has (or edited) wins over ours.
        if (e.author != null && Frontmatter.text(content, "author") == null) set["author"] = Frontmatter.quote(e.author)
        if (e.thumbnail != null && Frontmatter.text(content, "thumbnail") == null) set["thumbnail"] = Frontmatter.quote(e.thumbnail)
        if (e.minutes != null && Frontmatter.text(content, "est_minutes") == null) set["est_minutes"] = e.minutes.toString()

        if (template != null && capture != null) {
            TemplateRender.properties(template, capture).forEach { (key, value) ->
                if (Frontmatter.text(content, key) == null) set[key] = value
            }
        }

        val tags = Frontmatter.tags(content)
        val templateTags = if (template == null || capture == null) emptyList() else TemplateRender.tags(template, capture)
        val merged = Frontmatter.mergeTags(
            Frontmatter.mergeTags(tags, Source.detect(url).tags),
            templateTags,
        )
        if (merged != tags) set["tags"] = Frontmatter.yamlList(merged) // untouched unless a tag is missing
        e.transcriptStatus?.let { set["transcript"] = it }
        val mergedContent = Frontmatter.set(content, set)
        val text = if (template != null && capture != null) {
            TemplateRender.body(template, capture).orEmpty()
        } else {
            e.text.orEmpty()
        }
        return Sections.withTranscript(mergedContent, e.transcript, url).let { withTranscript ->
            set["extracted"] = status
            Sections.withText(Frontmatter.set(withTranscript, set), text)
        }
    }

    /** The stub title written at share time: empty, the generic fallback, the host or the URL. */
    private fun isPlaceholder(title: String?, url: String): Boolean {
        if (title.isNullOrBlank() || title == "Saved link" || title == url) return true
        return title == runCatching { URI(url).host }.getOrNull()
    }
}
