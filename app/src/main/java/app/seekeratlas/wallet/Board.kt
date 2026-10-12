package app.seekeratlas.wallet

import app.seekeratlas.game.Daily

data class BoardEntry(val day: Long, val score: Int, val player: String, val sgtMint: String?) {
    val verified: Boolean get() = sgtMint != null
}

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
}
