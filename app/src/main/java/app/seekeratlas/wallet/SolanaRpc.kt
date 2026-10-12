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

    /** Public RPC rate-limits hard; back off and retry on HTTP 429. */
    suspend fun call(method: String, params: JsonArray): JsonElement {
        repeat(3) { attempt ->
            try {
                return callOnce(method, params)
            } catch (e: RateLimited) {
                kotlinx.coroutines.delay(700L * (attempt + 1))
            }
        }
        return callOnce(method, params)
    }

    private class RateLimited : Exception("RPC rate limited")

    private suspend fun callOnce(method: String, params: JsonArray): JsonElement = withContext(Dispatchers.IO) {
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
        val text = try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode == 429) throw RateLimited()
            if (conn.responseCode !in 200..299) throw RpcException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val json = Json.parseToJsonElement(text).jsonObject
        json["error"]?.let { throw RpcException(it.toString()) }
        json["result"] ?: throw RpcException("empty result")
    }

    data class Blockhash(val hash: String, val slot: Long)

    /** Blockhash plus the slot it was read at; Phantom requires that slot as minContextSlot. */
    suspend fun latestBlockhash(): Blockhash {
        val r = call("getLatestBlockhash", buildJsonArray { add(buildJsonObject { put("commitment", "confirmed") }) }).jsonObject
        return Blockhash(
            hash = r["value"]!!.jsonObject["blockhash"]!!.jsonPrimitive.content,
            slot = r["context"]!!.jsonObject["slot"]!!.jsonPrimitive.content.toLong(),
        )
    }

    /** Balance of the SKR associated token account — the account tips and burns are paid from. */
    suspend fun skrBalance(owner: String): Double {
        val ata = Transactions.skrAta(owner)
        val info = call("getAccountInfo", buildJsonArray {
            add(JsonPrimitive(ata))
            add(buildJsonObject { put("encoding", "jsonParsed") })
        }).jsonObject["value"]
        if (info == null || info is kotlinx.serialization.json.JsonNull) return 0.0
        return info.jsonObject["data"]!!.jsonObject["parsed"]!!.jsonObject["info"]!!
            .jsonObject["tokenAmount"]!!.jsonObject["uiAmount"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    }

    /** true = confirmed, false = failed on-chain, null = still unknown after the timeout. */
    suspend fun awaitConfirmation(signature: String, timeoutMs: Long = 45_000): Boolean? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val status = runCatching {
                call("getSignatureStatuses", buildJsonArray {
                    add(buildJsonArray { add(JsonPrimitive(signature)) })
                    add(buildJsonObject { put("searchTransactionHistory", true) })
                }).jsonObject["value"]!!.jsonArray.first()
            }.getOrNull()
            if (status != null && status !is kotlinx.serialization.json.JsonNull) {
                val o = status.jsonObject
                if (o["err"] != null && o["err"] !is kotlinx.serialization.json.JsonNull) return false
                val level = o["confirmationStatus"]?.jsonPrimitive?.content
                if (level == "confirmed" || level == "finalized") return true
            }
            kotlinx.coroutines.delay(1_500)
        }
        return null
    }

    /** Returns the wallet's Seeker Genesis Token mint, or null if it holds none. */
    suspend fun genesisMint(owner: String): String? {
        val accounts = call("getTokenAccountsByOwner", buildJsonArray {
            add(JsonPrimitive(owner))
            add(buildJsonObject { put("programId", TOKEN_2022) })
            add(buildJsonObject { put("encoding", "jsonParsed") })
        })
        for (batch in Genesis.candidateMints(accounts).distinct().chunked(100)) {
            val infos = call("getMultipleAccounts", buildJsonArray {
                add(buildJsonArray { batch.forEach { add(JsonPrimitive(it)) } })
                add(buildJsonObject { put("encoding", "jsonParsed") })
            })
            Genesis.genesisMint(batch, infos)?.let { return it }
        }
        return null
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
