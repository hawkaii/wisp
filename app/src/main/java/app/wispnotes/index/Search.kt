package app.wispnotes.index

/** Library queries over the index: search, tag and rating filters, grouping by status. */
object NoteSearch {
    /** Every word of [query] must appear in the title, body or tags. */
    fun filter(all: List<NoteEntry>, query: String, tag: String?, minRating: Int?): List<NoteEntry> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return all.filter { e ->
            (tag == null || e.tagList().any { it.equals(tag, ignoreCase = true) }) &&
                (minRating == null || (e.rating ?: 0) >= minRating) &&
                words.all { w -> e.title.contains(w, true) || e.body.contains(w, true) || e.tags.contains(w, true) }
        }
    }

    /** Tags by use, most used first. */
    fun tags(all: List<NoteEntry>): List<String> =
        all.flatMap { it.tagList().map(String::lowercase) }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareBy({ -it.value }, { it.key })).map { it.key }

    private val ORDER = listOf("in-progress", "queued", "done", "archived")

    fun groupByStatus(list: List<NoteEntry>): List<Pair<String, List<NoteEntry>>> {
        val by = list.groupBy { it.status }
        return (ORDER + (by.keys - ORDER.toSet()).sorted()).mapNotNull { s -> by[s]?.let { s to it } }
    }
}
