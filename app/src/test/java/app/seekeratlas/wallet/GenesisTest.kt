package app.seekeratlas.wallet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenesisTest {
    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)

    @Test fun candidateMintsAreSingleNonDivisibleTokens() {
        val accounts = j("""{"value":[
          {"account":{"data":{"parsed":{"info":{"mint":"M1","tokenAmount":{"amount":"1","decimals":0}}}}}},
          {"account":{"data":{"parsed":{"info":{"mint":"M2","tokenAmount":{"amount":"500","decimals":6}}}}}},
          {"account":{"data":{"parsed":{"info":{"mint":"M3","tokenAmount":{"amount":"0","decimals":0}}}}}}
        ]}""")
        assertEquals(listOf("M1"), Genesis.candidateMints(accounts))
    }

    @Test fun findsMintThatIsMemberOfSeekerGroup() {
        val mints = j("""{"value":[
          {"data":{"parsed":{"info":{"extensions":[{"extension":"tokenGroupMember","state":{"group":"OTHER","mint":"M1"}}]}}}},
          {"data":{"parsed":{"info":{"extensions":[{"extension":"tokenGroupMember","state":{"group":"GT22s89nU4iWFkNXj1Bw6uYhJJWDRPpShHt4Bk8f99Te","mint":"M2"}}]}}}}
        ]}""")
        assertEquals("M2", Genesis.genesisMint(listOf("M1", "M2"), mints))
    }

    @Test fun noGroupMemberMeansNotVerified() {
        val mints = j("""{"value":[null,{"data":{"parsed":{"info":{}}}}]}""")
        assertNull(Genesis.genesisMint(listOf("M1", "M2"), mints))
    }
}
