package app.wispnotes.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/** The rule that decides what a shared reel does. Pure, so it is checked without a device. */
class ReelPolicyTest {
    @Test fun offByDefaultOnlySaves() {
        assertEquals(ReelAction.SaveOnly, ReelPolicy.action(summarise = false, loggedIn = true))
        assertEquals(ReelAction.SaveOnly, ReelPolicy.action(summarise = false, loggedIn = false))
        assertEquals(ReelAction.SaveOnly, ReelPolicy.action(summarise = false, loggedIn = null))
    }

    @Test fun onAndSignedInReadsTheReel() {
        assertEquals(ReelAction.ReadNow, ReelPolicy.action(summarise = true, loggedIn = true))
    }

    @Test fun onButSignedOutSaysSoInsteadOfOpeningAReaderThatWouldHitAWall() {
        assertEquals(ReelAction.SaveAndWarnLogin, ReelPolicy.action(summarise = true, loggedIn = false))
    }

    /** A fresh install has no answer yet. That is not "signed out", so it must not nag. */
    @Test fun unknownLoginOpensTheReaderAndLetsThePageDecide() {
        assertEquals(ReelAction.ReadNow, ReelPolicy.action(summarise = true, loggedIn = null))
    }
}
