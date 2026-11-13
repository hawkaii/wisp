package app.wispnotes.note

/** The mock "Publish" preview: a blog draft built from the title, the rating and the user's notes. Nothing leaves the phone. */
object Publish {
    fun stars(rating: Int) = "★".repeat(rating.coerceIn(0, 5)) + "☆".repeat(5 - rating.coerceIn(0, 5))

    fun draft(title: String, rating: Int?, notes: String): String = buildString {
        append("# ").append(title).append("\n\n")
        if (rating != null) append(stars(rating)).append("  ").append(rating).append("/5\n\n")
        append(notes.trim().ifEmpty { "(no notes yet)" })
    }
}
