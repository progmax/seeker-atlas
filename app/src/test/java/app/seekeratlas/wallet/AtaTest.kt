package app.seekeratlas.wallet

import org.junit.Assert.assertEquals
import org.junit.Test

class AtaTest {
    /** Real mainnet SKR account of a test wallet (checked with getTokenAccountsByOwner). */
    @Test fun skrAtaMatchesOnChainAccount() {
        assertEquals("7eehpNme", Transactions.skrAta("AwghTQUQV8cTVy9ENsjCY7gaxvr8RuZMacNYFdGbfKCT").take(8))
    }
}
