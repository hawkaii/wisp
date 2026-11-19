package app.wispnotes.vault

import app.wispnotes.index.RawNote
import app.wispnotes.note.Frontmatter
import app.wispnotes.note.Source

/** A note that already holds a link. */
data class SavedLink(
    /** Path relative to the vault root. */
    val path: String,
    /** The note's title, or its file name when the note has none. */
    val title: String,
)

/**
 * Finds a link that is already in the vault, so a second share can ask instead of writing a copy.
 *
 * The check is on the note's `url:`, not on its file name: the name carries the save date, so the
 * same link saved tomorrow would otherwise look like a different note. A note the Web Clipper made
 * keeps the address in `source:` when it has no `url:`, and that counts too. Tracking params are
 * dropped first, because sharing the same reel twice hands out `?igsh=` with two different values.
 */
object Duplicates {
    /** The first note in [notes] that holds [url], or null. A blank [url] never matches. */
    fun match(notes: List<RawNote>, url: String): SavedLink? {
        val wanted = Source.cleanUrl(url.trim())
        if (wanted.isBlank()) return null
        val note = notes.firstOrNull { savedUrl(it.content)?.let(Source::cleanUrl) == wanted } ?: return null
        return SavedLink(
            path = note.path,
            title = Frontmatter.text(note.content, "title")
                ?: note.path.substringAfterLast('/').removeSuffix(".md"),
        )
    }

    /** Wisp writes `url:`. The Web Clipper writes the address in `source:` instead. */
    private fun savedUrl(content: String): String? =
        Frontmatter.text(content, "url")?.takeIf { it.isNotBlank() }
            ?: Frontmatter.text(content, "source")?.takeIf { it.startsWith("http") }
}
