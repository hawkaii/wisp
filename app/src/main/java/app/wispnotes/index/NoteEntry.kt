package app.wispnotes.index

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.wispnotes.note.Frontmatter
import app.wispnotes.note.Review
import java.time.LocalDate

/** One row of the Room index. The vault is the truth: this table can be deleted and rebuilt any time. */
@Entity(tableName = "notes")
data class NoteEntry(
    @PrimaryKey val fileName: String,
    val title: String,
    val source: String,
    val type: String,
    val url: String,
    val status: String,
    val estMinutes: Int?,
    val thumbnail: String?,
    val tags: String,
    val saved: String,
    val extracted: String,
    val rating: Int? = null,
    val finished: String? = null,
    val nextReview: String? = null,
    val reviewStep: Int? = null,
    val extractAttempts: Int = 0,
    /** Text after the frontmatter, for search. shortcut: capped, long transcripts are searched only in their first part. */
    val body: String = "",
) {
    fun tagList() = tags.split(',').filter { it.isNotBlank() }

    fun isDue(today: LocalDate) = Review.isDue(status, nextReview, finished, today)


    companion object {
        private const val MAX_BODY = 20_000

        /** The first `YYYY-MM-DD` of a date property. A clipper may write a full timestamp after it. */
        private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

        /**
         * One row from one file. [path] is the file's path relative to the vault root.
         *
         * A note Wisp wrote always has `source:`. A note the user wrote, or one the Obsidian Web
         * Clipper wrote into `Clippings/`, may have none of Wisp's keys, so every field has a
         * fallback: the title from the first `# ` heading, the source from the URL a clipper stores
         * in `source:`, the date from `created` or `published`. Nothing is written back to the file.
         */
        fun parse(path: String, content: String): NoteEntry? {
            val name = path.substringAfterLast('/')
            val rawSource = Frontmatter.text(content, "source")
            val url = Frontmatter.text(content, "url")
                ?: rawSource?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: ""
            return NoteEntry(
                fileName = path,
                title = Frontmatter.text(content, "title") ?: heading(content) ?: name.removeSuffix(".md"),
                source = sourceLabel(rawSource),
                type = Frontmatter.text(content, "type") ?: "note",
                url = url,
                status = Frontmatter.text(content, "status") ?: "queued",
                estMinutes = Frontmatter.text(content, "est_minutes")?.toIntOrNull(),
                thumbnail = Frontmatter.text(content, "thumbnail"),
                tags = Frontmatter.tags(content).joinToString(","),
                saved = savedDate(content),
                // A note with `extracted:` is ours and may still be waiting for the reader. A foreign
                // note already contains its text, so it must not be queued for extraction.
                extracted = Frontmatter.text(content, "extracted") ?: "done",
                rating = Frontmatter.text(content, "rating")?.toIntOrNull(),
                finished = Frontmatter.text(content, "finished"),
                nextReview = Frontmatter.text(content, "next_review"),
                reviewStep = Frontmatter.text(content, "review_step")?.toIntOrNull(),
                extractAttempts = Frontmatter.text(content, "extract_attempts")?.toIntOrNull() ?: 0,
                body = Frontmatter.body(content).take(MAX_BODY),
            )
        }

        /** The first `# Heading` in the body, which is how an Obsidian note usually names itself. */
        private fun heading(content: String): String? =
            Frontmatter.body(content).lineSequence()
                .firstOrNull { it.startsWith("# ") }
                ?.removePrefix("# ")?.trim()?.takeIf { it.isNotEmpty() }

        /**
         * A clipper often stores the page address in `source:`. A URL is a poor label on a card, so
         * the note shows the site instead and keeps the address in `url:` for opening and duplicates.
         */
        private fun sourceLabel(raw: String?): String = when {
            raw == null -> "obsidian"
            raw.startsWith("http://") || raw.startsWith("https://") ->
                runCatching { java.net.URI(raw).host?.removePrefix("www.") ?: raw }.getOrDefault(raw)
            else -> raw
        }

        /** `saved:` first, because that one is ours. Then the two keys a clipper writes. */
        private fun savedDate(content: String): String {
            val raw = Frontmatter.text(content, "saved")
                ?: Frontmatter.text(content, "created")
                ?: Frontmatter.text(content, "published")
                ?: return ""
            return ISO_DATE.find(raw)?.value ?: raw
        }
    }
}

/** One file from the vault: its path relative to the root, and its text. */
class RawNote(val path: String, val content: String)

object Indexer {
    /** Reads every note in the vault and replaces the whole index with them. */
    suspend fun rescan(read: suspend () -> List<RawNote>, replace: suspend (List<NoteEntry>) -> Unit) {
        replace(read().mapNotNull { NoteEntry.parse(it.path, it.content) })
    }

    fun pending(entries: List<NoteEntry>): List<NoteEntry> = entries.filter { it.extracted == "pending" }
}
