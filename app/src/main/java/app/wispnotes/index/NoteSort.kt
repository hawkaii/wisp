package app.wispnotes.index

/** How the home grid is ordered. [label] is the text on the chip, so the UI has no second list to keep in step. */
enum class Sort(val label: String) {
    Latest("Latest"),
    Oldest("Oldest"),
    Title("A–Z"),
    Rating("Rating"),
}

/**
 * Orders the library for the home grid.
 *
 * `saved` is a `YYYY-MM-DD` string, so a plain string sort is a date sort. A note with no date sorts
 * last under [Sort.Latest], which is where it belongs: it is not one of the newest.
 */
object NoteSort {
    fun apply(all: List<NoteEntry>, sort: Sort): List<NoteEntry> = when (sort) {
        Sort.Latest -> all.sortedByDescending { it.saved }
        Sort.Oldest -> all.sortedBy { it.saved }
        Sort.Title -> all.sortedBy { it.title.lowercase() }
        // Unrated notes are not a 0-star note: they go last, then by date.
        Sort.Rating -> all.sortedWith(
            compareByDescending<NoteEntry> { it.rating ?: -1 }.thenByDescending { it.saved },
        )
    }
}
