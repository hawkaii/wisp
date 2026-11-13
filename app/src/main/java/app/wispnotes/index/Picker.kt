package app.wispnotes.index

/** "What now?": up to three queued notes that fit the time, best fit first. The first one is the default pick. */
object Picker {
    /** A note with no length yet counts as this many minutes. */
    const val UNKNOWN_MINUTES = 10

    fun pick(queue: List<NoteEntry>, minutes: Int): List<NoteEntry> {
        val known = queue.filter { it.status == "queued" && it.type != "live" && it.estMinutes != null && it.estMinutes <= minutes }
        val candidates = if (known.isNotEmpty()) known else queue.filter { it.status == "queued" && it.type != "live" && it.estMinutes == null }
        return candidates
            // Fullest use of the time first; among equals, the one waiting longest.
            .sortedWith(compareBy({ minutes - (it.estMinutes ?: UNKNOWN_MINUTES) }, { it.saved }, { it.fileName }))
            .take(3)
    }
}
