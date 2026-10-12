package app.seekeratlas.wallet

import app.seekeratlas.Config
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Seeker Genesis Token detection from jsonParsed RPC responses. */
object Genesis {
    /** From getTokenAccountsByOwner(Token-2022): mints of NFT-like balances (amount 1, decimals 0). */
    fun candidateMints(result: JsonElement): List<String> =
        result.jsonObject["value"]!!.jsonArray.mapNotNull { acc ->
            val info = acc.obj("account")?.obj("data")?.obj("parsed")?.obj("info") ?: return@mapNotNull null
            val amount = info.obj("tokenAmount") ?: return@mapNotNull null
            val isSingle = amount["amount"]?.jsonPrimitive?.content == "1" &&
                amount["decimals"]?.jsonPrimitive?.content == "0"
            if (isSingle) info["mint"]?.jsonPrimitive?.content else null
        }

    /** From getMultipleAccounts(mints): the first mint that is a member of the Seeker Genesis group. */
    fun genesisMint(mints: List<String>, result: JsonElement): String? {
        val values = result.jsonObject["value"]!!.jsonArray
        values.forEachIndexed { i, v ->
            if (v is JsonNull) return@forEachIndexed
            val extensions = v.obj("data")?.obj("parsed")?.obj("info")?.get("extensions")?.jsonArray ?: return@forEachIndexed
            val member = extensions.any { ext ->
                val e = ext.jsonObject
                e["extension"]?.jsonPrimitive?.content == "tokenGroupMember" &&
                    e.obj("state")?.get("group")?.jsonPrimitive?.content == Config.SGT_GROUP
            }
            if (member) return mints[i]
        }
        return null
    }

    private fun JsonElement.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject
}
