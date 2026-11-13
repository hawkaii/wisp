package app.wispnotes.index

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.wispnotes.note.Frontmatter

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
) {
    companion object {
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
}
