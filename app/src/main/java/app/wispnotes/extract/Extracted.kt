package app.wispnotes.extract

/** What an extractor found. Every field is optional: pages give what they give. */
data class Extracted(
    val title: String? = null,
    val author: String? = null,
    val thumbnail: String? = null,
    val minutes: Int? = null,
    val text: String? = null,
) {
    /** done: a title and a length or text. partial: something. failed: nothing (link only). */
    val status: String
        get() = when {
            title != null && (text != null || minutes != null) -> "done"
            title != null || author != null || thumbnail != null || minutes != null || text != null -> "partial"
            else -> "failed"
        }
}

/** Blocking GET. Returns the body, or null on any failure. Callers run it off the main thread. */
typealias Fetch = (url: String, headers: Map<String, String>) -> String?

const val WORDS_PER_MINUTE = 230

fun readingMinutes(text: String): Int? {
    val words = text.split(Regex("\\s+")).count { it.isNotEmpty() }
    return if (words == 0) null else maxOf(1, (words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE)
}
