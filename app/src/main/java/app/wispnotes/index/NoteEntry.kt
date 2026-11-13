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
    /** Text after the frontmatter, for search. shortcut: capped, long transcripts are searched only in their first part. */
    val body: String = "",
) {
    fun tagList() = tags.split(',').filter { it.isNotBlank() }

    fun isDue(today: LocalDate) = Review.isDue(status, nextReview, finished, today)


    companion object {
        private const val MAX_BODY = 20_000

        /** Null when the file has no Wisp frontmatter (not our note). */
        fun parse(fileName: String, content: String): NoteEntry? {
            if (Frontmatter.text(content, "source") == null) return null
            return NoteEntry(
                fileName = fileName,
                title = Frontmatter.text(content, "title") ?: fileName.removeSuffix(".md"),
                source = Frontmatter.text(content, "source").orEmpty(),
                type = Frontmatter.text(content, "type").orEmpty(),
                url = Frontmatter.text(content, "url").orEmpty(),
                status = Frontmatter.text(content, "status") ?: "queued",
                estMinutes = Frontmatter.text(content, "est_minutes")?.toIntOrNull(),
                thumbnail = Frontmatter.text(content, "thumbnail"),
                tags = Frontmatter.tags(content).joinToString(","),
                saved = Frontmatter.text(content, "saved").orEmpty(),
                extracted = Frontmatter.text(content, "extracted") ?: "pending",
                rating = Frontmatter.text(content, "rating")?.toIntOrNull(),
                finished = Frontmatter.text(content, "finished"),
                nextReview = Frontmatter.text(content, "next_review"),
                reviewStep = Frontmatter.text(content, "review_step")?.toIntOrNull(),
                body = Frontmatter.body(content).take(MAX_BODY),
            )
        }
    }
}

class RawNote(val fileName: String, val content: String)

object Indexer {
    /** Reads every note in the vault and replaces the whole index with them. */
    suspend fun rescan(read: suspend () -> List<RawNote>, replace: suspend (List<NoteEntry>) -> Unit) {
        replace(read().mapNotNull { NoteEntry.parse(it.fileName, it.content) })
    }

    fun pending(entries: List<NoteEntry>): List<NoteEntry> = entries.filter { it.extracted == "pending" }
}
