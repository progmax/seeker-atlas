package app.seekeratlas.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardTest {
    private val player = "9xQeWvG816bUx9EPjHmaT23yvVM2ZWbrrpZb9PusVFin"
    private val sgt = "5Hp1BpZgXQbnf9ruPTyY4AsDGQe2jjUN1q2DwJ9yZcQE"

    @Test fun memoRoundTrips() {
        val memo = Board.memo(day = 20_738, score = 12_750, player = player, sgtMint = sgt)
        assertEquals("atlas1|20738|12750|$player|$sgt", memo)
        assertEquals(BoardEntry(20_738, 12_750, player, sgt), Board.parse(memo))
    }

    @Test fun unverifiedPlayerUsesDash() {
        val memo = Board.memo(20_738, 100, player, null)
        assertEquals(BoardEntry(20_738, 100, player, null), Board.parse(memo))
    }

    @Test fun parsesRpcMemoWithLengthPrefix() {
        val e = Board.parse("[64] atlas1|20738|9000|$player|-")
        assertEquals(9000, e?.score)
    }

    @Test fun rejectsGarbageAndImpossibleScores() {
        assertNull(Board.parse("hello"))
        assertNull(Board.parse("atlas1|20738|99999|$player|-"))
        assertNull(Board.parse("atlas1|x|1|$player|-"))
    }

    @Test fun rankingKeepsBestPerSeekerAndDay() {
        val entries = listOf(
            BoardEntry(20_738, 9000, "A", "sgt1"),
            BoardEntry(20_738, 15000, "B", "sgt1"),   // same device, second wallet: one Seeker, one entry
            BoardEntry(20_738, 12000, "C", null),
            BoardEntry(20_737, 25000, "D", "sgt2"),  // yesterday
            BoardEntry(20_738, 11000, "C", null),
        )
        val ranked = Board.rank(entries, day = 20_738)
        assertEquals(listOf("B", "C"), ranked.map { it.player })
        assertEquals(listOf(15000, 12000), ranked.map { it.score })
    }
}
