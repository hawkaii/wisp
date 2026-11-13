package app.wispnotes.note

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewTest {
    private val d = LocalDate.of(2026, 11, 20)

    @Test fun firstReviewIsThreeDaysAfterFinishing() {
        assertEquals(Review.Plan(0, LocalDate.of(2026, 11, 23)), Review.first(d))
    }

    @Test fun rememberedClimbsThroughAllSteps() {
        var step = 0
        val days = mutableListOf<Long>()
        repeat(5) {
            val p = Review.after(step, Recall.Remembered, d)
            step = p.step
            days += java.time.temporal.ChronoUnit.DAYS.between(d, p.next)
        }
        assertEquals(listOf(7L, 21L, 60L, 60L, 60L), days) // the last step repeats
    }

    @Test fun fuzzyRepeatsAndForgotStartsOver() {
        assertEquals(Review.Plan(2, d.plusDays(21)), Review.after(2, Recall.Fuzzy, d))
        assertEquals(Review.Plan(0, d.plusDays(3)), Review.after(3, Recall.Forgot, d))
    }

    @Test fun badStepIsClamped() {
        assertEquals(1, Review.after(-4, Recall.Remembered, d).step)
        assertEquals(3, Review.after(99, Recall.Fuzzy, d).step)
    }

    @Test fun dueOnlyWhenDoneAndDateReached() {
        assertTrue(Review.isDue("done", "2026-11-20", null, d))
        assertTrue(Review.isDue("done", "2026-11-01", null, d))
        assertFalse(Review.isDue("done", "2026-11-21", null, d))
        assertFalse(Review.isDue("queued", "2026-11-01", null, d))
    }

    @Test fun doneByHandInObsidianFallsBackToFinished() {
        assertTrue(Review.isDue("done", null, "2026-11-17", d)) // +3 days = today
        assertFalse(Review.isDue("done", null, "2026-11-18", d))
        assertNull(Review.dueDate(null, null))
        assertFalse(Review.isDue("done", null, null, d))
    }

    @Test fun unreadableDatesNeverCrash() {
        assertFalse(Review.isDue("done", "next tuesday", "", d))
        assertEquals(LocalDate.of(2026, 11, 3), Review.dueDate("garbage", "2026-10-31"))
    }

    @Test fun finishWritesRatingStatusAndSchedule() {
        val v = Review.finishValues(9, d) // out of range is clamped
        assertEquals("5", v["rating"])
        assertEquals("done", v["status"])
        assertEquals("2026-11-20", v["finished"])
        assertEquals("2026-11-23", v["next_review"])
        assertEquals("0", v["review_step"])
    }

    @Test fun noteBeforeRateNeedsTwoLines() {
        assertFalse(Review.hasEnoughNotes(""))
        assertFalse(Review.hasEnoughNotes("one line\n\n  \n"))
        assertTrue(Review.hasEnoughNotes("one\ntwo"))
    }
}
