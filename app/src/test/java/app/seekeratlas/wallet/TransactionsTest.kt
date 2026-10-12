package app.seekeratlas.wallet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.sol4k.Constants.TOKEN_PROGRAM_ID
import org.sol4k.PublicKey

class TransactionsTest {
    @Test fun burnCheckedEncodesFiveSkr() {
        val ix = Transactions.BurnChecked(
            PublicKey("GT2zuHVaZQYZSyQMgJPLzvkmyztfyXg2NJunqFp4p3A4"),
            PublicKey("SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3"),
            PublicKey("GT2zuHVaZQYZSyQMgJPLzvkmyztfyXg2NJunqFp4p3A4"),
            amount = 5_000_000, decimals = 6,
        )
        // 5_000_000 = 0x4C4B40, little-endian u64
        assertArrayEquals(byteArrayOf(15, 0x40, 0x4B, 0x4C, 0, 0, 0, 0, 0, 6), ix.data)
        assertEquals(TOKEN_PROGRAM_ID, ix.programId)
        assertEquals(listOf(true, true, false), ix.keys.map { it.writable })
        assertEquals(listOf(false, false, true), ix.keys.map { it.signer })
    }
}
