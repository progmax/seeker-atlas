package app.seekeratlas.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardTest {
    private val player = "9xQeWvG816bUx9EPjHmaT23yvVM2ZWbrrpZb9PusVFin"
    private val sgt = "5Hp1BpZgXQbnf9ruPTyY4AsDGQe2jjUN1q2DwJ9yZcQE"

    @Test fun memoRoundTrips() {
        val memo = Board.memo(day = 20_738, score = 12_750, player = player, sgtMint = sgt)
        assertEquals("atlas1|20738|12750|$player|$sgt|0", memo)
        assertEquals(BoardEntry(20_738, 12_750, player, sgt), Board.parse(memo))
    }

    @Test fun unverifiedPlayerUsesDash() {
        val memo = Board.memo(20_738, 100, player, null)
        assertEquals(BoardEntry(20_738, 100, player, null), Board.parse(memo))
    }

    @Test fun hintsAreRecordedAndOldFiveFieldMemosStillParse() {
        val memo = Board.memo(20_738, 9000, player, null, hints = 2)
        assertEquals(2, Board.parse(memo)?.hints)
        assertEquals(0, Board.parse("atlas1|20738|9000|$player|-")?.hints)
        assertNull(Board.parse("atlas1|20738|9000|$player|-|99"))
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

    @Test fun verificationDropsForgedPlayerAndStripsForgedGenesis() = kotlinx.coroutines.runBlocking {
        val honest = SignedEntry("sig1", BoardEntry(20_738, 20_000, "Alice", "sgtA"))
        val forgedPlayer = SignedEntry("sig2", BoardEntry(20_738, 25_000, "Victim", "sgtV"))
        val forgedGenesis = SignedEntry("sig3", BoardEntry(20_738, 22_000, "Bob", "sgtA"))
        val signers = mapOf("sig1" to "Alice", "sig2" to "Mallory", "sig3" to "Bob")
        val genesis = mapOf("Alice" to "sgtA", "Bob" to null)
        val verified = Board.verify(
            listOf(honest, forgedPlayer, forgedGenesis),
            signerOf = { signers[it] },
            genesisOf = { genesis[it] },
        )
        assertEquals(
            listOf(BoardEntry(20_738, 22_000, "Bob", null), BoardEntry(20_738, 20_000, "Alice", "sgtA")),
            verified.sortedByDescending { it.score },
        )
    }
}
