package app.seekeratlas.wallet

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Base64

/** Hits mainnet RPC. Run with ATLAS_NETWORK_TESTS=1. */
class NetworkSimulationTest {
    private val rpc = SolanaRpc()
    private val funded = "GT2zuHVaZQYZSyQMgJPLzvkmyztfyXg2NJunqFp4p3A4"

    @Test fun scoreMemoSimulatesOnMainnet() = runBlocking {
        assumeTrue(System.getenv("ATLAS_NETWORK_TESTS") == "1")
        val memo = Board.memo(20_738, 12_345, funded, null)
        val tx = Transactions.scoreMemo(funded, memo, rpc.latestBlockhash())
        val sim = rpc.call("simulateTransaction", buildJsonArray {
            add(JsonPrimitive(Base64.getEncoder().encodeToString(tx)))
            add(buildJsonObject { put("encoding", "base64"); put("sigVerify", false); put("replaceRecentBlockhash", true) })
        }).jsonObject["value"]!!.jsonObject
        println(sim["logs"])
        assertEquals(JsonNull, sim["err"])
    }
}
