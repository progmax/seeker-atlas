package app.seekeratlas.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DailyTest {
    @Test fun picksFiveDistinctIndices() {
        val picks = Daily.pick(epochDay = 20_738, poolSize = 50)
        assertEquals(5, picks.size)
        assertEquals(5, picks.toSet().size)
        assert(picks.all { it in 0 until 50 })
    }

    @Test fun sameDaySamePicks() {
        assertEquals(Daily.pick(20_738, 50), Daily.pick(20_738, 50))
    }

    @Test fun differentDaysDiffer() {
        assertNotEquals(Daily.pick(20_738, 50), Daily.pick(20_739, 50))
    }

    @Test fun noRepeatsUntilPoolIsExhausted() {
        val seen = (0 until 10).flatMap { Daily.pick(Daily.LAUNCH_EPOCH_DAY + it, 50) }
        assertEquals(50, seen.toSet().size)
    }

    @Test fun smallPoolTakesWhatExists() {
        assertEquals(3, Daily.pick(1, 3).size)
    }

    @Test fun puzzleNumberStartsAtOneOnLaunchDay() {
        assertEquals(1, Daily.puzzleNumber(Daily.LAUNCH_EPOCH_DAY))
        assertEquals(8, Daily.puzzleNumber(Daily.LAUNCH_EPOCH_DAY + 7))
    }

    @Test fun shareTextShowsSquaresAndTotal() {
        val text = Daily.shareText(puzzle = 3, scores = listOf(5000, 4200, 2600, 900, 50))
        assertEquals("Seeker Atlas #3 — 12750/25000\n🟩🟩🟨🟧🟥", text)
    }
}
