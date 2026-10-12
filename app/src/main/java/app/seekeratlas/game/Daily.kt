package app.seekeratlas.game

import kotlin.random.Random

object Daily {
    const val ROUNDS = 5
    /** 2026-10-12, the day Seeker Atlas launched. */
    const val LAUNCH_EPOCH_DAY = 20_738L

    fun pick(epochDay: Long, poolSize: Int): List<Int> =
        (0 until poolSize).shuffled(Random(epochDay * 7919 + 17)).take(ROUNDS)

    fun puzzleNumber(epochDay: Long): Int = (epochDay - LAUNCH_EPOCH_DAY + 1).toInt()

    fun shareText(puzzle: Int, scores: List<Int>): String {
        val squares = scores.joinToString("") {
            when {
                it >= 4000 -> "🟩"
                it >= 2500 -> "🟨"
                it >= 800 -> "🟧"
                else -> "🟥"
            }
        }
        return "Seeker Atlas #$puzzle — ${scores.sum()}/${ROUNDS * 5000}\n$squares"
    }
}
