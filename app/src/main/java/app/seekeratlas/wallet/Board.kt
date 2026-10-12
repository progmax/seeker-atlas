package app.seekeratlas.wallet

import app.seekeratlas.game.Daily

data class BoardEntry(val day: Long, val score: Int, val player: String, val sgtMint: String?) {
    val verified: Boolean get() = sgtMint != null
}

data class SignedEntry(val signature: String, val entry: BoardEntry)

/** On-chain daily leaderboard: each result is a memo transaction that references [app.seekeratlas.Config.BOARD_ADDRESS]. */
object Board {
    private const val PREFIX = "atlas1"
    private val MAX_TOTAL = Daily.ROUNDS * 5000

    fun memo(day: Long, score: Int, player: String, sgtMint: String?): String =
        listOf(PREFIX, day, score, player, sgtMint ?: "-").joinToString("|")

    fun parse(raw: String): BoardEntry? {
        val start = raw.indexOf("$PREFIX|").takeIf { it >= 0 } ?: return null
        val parts = raw.substring(start).trim().split("|")
        if (parts.size != 5) return null
        val day = parts[1].toLongOrNull() ?: return null
        val score = parts[2].toIntOrNull()?.takeIf { it in 0..MAX_TOTAL } ?: return null
        val player = parts[3].takeIf { it.length in 32..44 } ?: return null
        return BoardEntry(day, score, player, parts[4].takeIf { it != "-" })
    }

    /** Best score per Seeker (Genesis Token) or, for unverified players, per wallet. */
    fun rank(entries: List<BoardEntry>, day: Long): List<BoardEntry> =
        entries.filter { it.day == day }
            .groupBy { it.sgtMint ?: "wallet:${it.player}" }
            .map { (_, group) -> group.maxBy { it.score } }
            .sortedByDescending { it.score }

    /**
     * Memo text is self-reported, so trust it only as far as the chain backs it: the transaction's
     * fee payer must be the claimed player, and a claimed Genesis Token must really be in that wallet.
     */
    suspend fun verify(
        entries: List<SignedEntry>,
        signerOf: suspend (signature: String) -> String?,
        genesisOf: suspend (player: String) -> String?,
    ): List<BoardEntry> {
        val genesisCache = mutableMapOf<String, String?>()
        return entries.mapNotNull { (sig, e) ->
            if (signerOf(sig) != e.player) return@mapNotNull null
            if (e.sgtMint == null) return@mapNotNull e
            val owned = genesisCache.getOrPut(e.player) { genesisOf(e.player) }
            if (owned == e.sgtMint) e else e.copy(sgtMint = null)
        }
    }
}
