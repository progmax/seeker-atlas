package app.seekeratlas.game

import kotlin.random.Random

object Daily {
    const val ROUNDS = 5
    /** 2026-10-12, the day Seeker Atlas launched. */
    const val LAUNCH_EPOCH_DAY = 20_738L

    /** Walks one fixed shuffle of the pool, five photos a day, so nothing repeats until the pool runs out. */
    fun pick(epochDay: Long, poolSize: Int): List<Int> {
        val order = (0 until poolSize).shuffled(Random(SEED))
        if (poolSize <= ROUNDS) return order
        val start = Math.floorMod((epochDay - LAUNCH_EPOCH_DAY) * ROUNDS, poolSize.toLong()).toInt()
        return List(ROUNDS) { order[(start + it) % poolSize] }
    }

    private const val SEED = 20_261_012L

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
