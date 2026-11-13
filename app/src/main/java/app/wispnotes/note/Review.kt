package app.wispnotes.note

import java.time.LocalDate

enum class Recall { Remembered, Fuzzy, Forgot }

/** Spaced revisit: a finished note comes back after 3, 7, 21 and 60 days. Pure date maths, no Android. */
object Review {
    val STEPS = listOf(3, 7, 21, 60)

    data class Plan(val step: Int, val next: LocalDate)

    fun first(finished: LocalDate) = Plan(0, finished.plusDays(STEPS[0].toLong()))

    /** Remembered moves up one step (the last step repeats), Fuzzy repeats this step, Forgot starts over. */
    fun after(step: Int, recall: Recall, today: LocalDate): Plan {
        val s = step.coerceIn(0, STEPS.lastIndex)
        val n = when (recall) {
            Recall.Remembered -> (s + 1).coerceAtMost(STEPS.lastIndex)
            Recall.Fuzzy -> s
            Recall.Forgot -> 0
        }
        return Plan(n, today.plusDays(STEPS[n].toLong()))
    }

    private fun date(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }

    /**
     * When a done note is due. A note marked done by hand in Obsidian has no `next_review`: it falls
     * back to `finished` + 3 days. A missing or unreadable date means "never due", not a crash.
     */
    fun dueDate(nextReview: String?, finished: String?): LocalDate? =
        date(nextReview) ?: date(finished)?.plusDays(STEPS[0].toLong())

    fun isDue(status: String, nextReview: String?, finished: String?, today: LocalDate): Boolean =
        status == "done" && dueDate(nextReview, finished)?.let { !it.isAfter(today) } == true

    /** The frontmatter written when the user finishes a note. */
    fun finishValues(rating: Int, today: LocalDate): Map<String, String> =
        linkedMapOf("rating" to rating.coerceIn(1, 5).toString(), "status" to "done", "finished" to today.toString()) +
            planValues(first(today))

    fun planValues(p: Plan): Map<String, String> =
        linkedMapOf("next_review" to p.next.toString(), "review_step" to p.step.toString())

    /** Note-before-rate: at least two non-blank lines in `## My notes` before the rating opens. */
    fun hasEnoughNotes(notes: String) = notes.lines().count { it.isNotBlank() } >= 2
}
