package app.wispnotes.extract

import app.wispnotes.note.Frontmatter
import app.wispnotes.note.Sections
import app.wispnotes.note.Source
import java.net.URI

/** Writes an extraction into a note. Touches only keys the app owns; `## My notes` is never read. */
object Merge {
    fun apply(content: String, e: Extracted, status: String = e.status): String {
        val url = Frontmatter.text(content, "url").orEmpty()
        val set = linkedMapOf<String, String>()

        if (e.title != null && isPlaceholder(Frontmatter.text(content, "title"), url)) set["title"] = Frontmatter.quote(e.title)
        // A value the user already has (or edited) wins over ours.
        if (e.author != null && Frontmatter.text(content, "author") == null) set["author"] = Frontmatter.quote(e.author)
        if (e.thumbnail != null && Frontmatter.text(content, "thumbnail") == null) set["thumbnail"] = Frontmatter.quote(e.thumbnail)
        if (e.minutes != null && Frontmatter.text(content, "est_minutes") == null) set["est_minutes"] = e.minutes.toString()
        val tags = Frontmatter.tags(content)
        val merged = Frontmatter.mergeTags(tags, Source.detect(url).tags)
        if (merged != tags) set["tags"] = Frontmatter.yamlList(merged) // untouched unless a source tag is missing
        set["extracted"] = status

        return Sections.withText(Frontmatter.set(content, set), e.text.orEmpty())
    }

    /** The stub title written at share time: empty, the generic fallback, the host or the URL. */
    private fun isPlaceholder(title: String?, url: String): Boolean {
        if (title.isNullOrBlank() || title == "Saved link" || title == url) return true
        return title == runCatching { URI(url).host }.getOrNull()
    }
}
