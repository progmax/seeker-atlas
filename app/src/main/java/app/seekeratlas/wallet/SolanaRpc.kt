package app.seekeratlas.wallet

import app.seekeratlas.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

class RpcException(message: String) : Exception(message)

class SolanaRpc(private val url: String = Config.RPC_URL) {

    suspend fun call(method: String, params: JsonArray): JsonElement = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("jsonrpc", "2.0"); put("id", 1); put("method", method); put("params", params)
        }.toString()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().use { it.readText() }
        val json = Json.parseToJsonElement(text).jsonObject
        json["error"]?.let { throw RpcException(it.toString()) }
        json["result"] ?: throw RpcException("empty result")
    }

    suspend fun latestBlockhash(): String =
        call("getLatestBlockhash", buildJsonArray { add(buildJsonObject { put("commitment", "finalized") }) })
            .jsonObject["value"]!!.jsonObject["blockhash"]!!.jsonPrimitive.content

    suspend fun skrBalance(owner: String): Double {
        val result = call("getTokenAccountsByOwner", buildJsonArray {
            add(JsonPrimitive(owner))
            add(buildJsonObject { put("mint", Config.SKR_MINT) })
            add(buildJsonObject { put("encoding", "jsonParsed") })
        })
        return result.jsonObject["value"]!!.jsonArray.sumOf { acc ->
            acc.jsonObject["account"]!!.jsonObject["data"]!!.jsonObject["parsed"]!!.jsonObject["info"]!!
                .jsonObject["tokenAmount"]!!.jsonObject["uiAmount"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        }
    }

    /** Returns the wallet's Seeker Genesis Token mint, or null if it holds none. */
    suspend fun genesisMint(owner: String): String? {
        val accounts = call("getTokenAccountsByOwner", buildJsonArray {
            add(JsonPrimitive(owner))
            add(buildJsonObject { put("programId", TOKEN_2022) })
            add(buildJsonObject { put("encoding", "jsonParsed") })
        })
        val mints = Genesis.candidateMints(accounts).take(100)
        if (mints.isEmpty()) return null
        val infos = call("getMultipleAccounts", buildJsonArray {
            add(buildJsonArray { mints.forEach { add(JsonPrimitive(it)) } })
            add(buildJsonObject { put("encoding", "jsonParsed") })
        })
        return Genesis.genesisMint(mints, infos)
    }

    suspend fun boardEntries(limit: Int = 1000): List<SignedEntry> {
        val sigs = call("getSignaturesForAddress", buildJsonArray {
            add(JsonPrimitive(Config.BOARD_ADDRESS))
            add(buildJsonObject { put("limit", limit) })
        })
        return sigs.jsonArray.mapNotNull { s ->
            val o = s.jsonObject
            if (o["err"]?.let { it !is kotlinx.serialization.json.JsonNull } == true) return@mapNotNull null
            val entry = o["memo"]?.jsonPrimitive?.contentOrNullSafe()?.let(Board::parse) ?: return@mapNotNull null
            SignedEntry(o["signature"]!!.jsonPrimitive.content, entry)
        }
    }

    /** First account key of a legacy/v0 transaction = fee payer = signer. */
    suspend fun feePayer(signature: String): String? {
        val tx = call("getTransaction", buildJsonArray {
            add(JsonPrimitive(signature))
            add(buildJsonObject { put("encoding", "json"); put("maxSupportedTransactionVersion", 0); put("commitment", "confirmed") })
        })
        if (tx is kotlinx.serialization.json.JsonNull) return null
        return tx.jsonObject["transaction"]!!.jsonObject["message"]!!.jsonObject["accountKeys"]!!.jsonArray
            .firstOrNull()?.jsonPrimitive?.content
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? = if (isString) content else null

    companion object {
        const val TOKEN_2022 = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
    }
}
