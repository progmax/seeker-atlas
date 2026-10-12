package app.seekeratlas.game

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.seekeratlas.Config
import app.seekeratlas.geo.Geo
import app.seekeratlas.geo.LatLon
import app.seekeratlas.wallet.Board
import app.seekeratlas.wallet.BoardEntry
import app.seekeratlas.wallet.SolanaRpc
import app.seekeratlas.wallet.Transactions
import app.seekeratlas.wallet.WalletConnector
import app.seekeratlas.wallet.WalletResult
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

private const val BOARD_VERIFY_LIMIT = 25
private const val TAG = "SeekerAtlas"

enum class Screen { Home, Round, Summary, Board }

data class RoundResult(val guess: LatLon, val distanceKm: Double, val score: Int)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("atlas", Context.MODE_PRIVATE)
    private val rpc = SolanaRpc()
    private val connector = WalletConnector()

    private val pool = Photos.load(app)
    var epochDay by mutableStateOf(today()); private set
    val puzzle: Int get() = Daily.puzzleNumber(epochDay)
    var rounds by mutableStateOf(roundsFor(epochDay)); private set

    var screen by mutableStateOf(Screen.Home); private set
    var roundIndex by mutableStateOf(0); private set
    var pendingGuess by mutableStateOf<LatLon?>(null); private set
    var results by mutableStateOf(loadResults()); private set

    var wallet by mutableStateOf(prefs.getString("wallet", null)); private set
    var genesisMint by mutableStateOf(prefs.getString("sgt", null)); private set
    var skrBalance by mutableStateOf<Double?>(null); private set
    var balanceError by mutableStateOf(false); private set
    var board by mutableStateOf<List<BoardEntry>>(emptyList()); private set
    var sealedSignature by mutableStateOf(prefs.getString("sealed_$epochDay", null)); private set
    /** Round indexes of today's puzzle where the player burned SKR for a hint. */
    var hinted by mutableStateOf(loadHints()); private set
    var busy by mutableStateOf(false); private set
    private var boardJob: Job? = null
    var message by mutableStateOf<String?>(null)

    val finished: Boolean get() = results.size >= rounds.size
    val total: Int get() = results.sumOf { it.score }
    val revealed: Boolean get() = results.size > roundIndex

    init {
        connector.authToken = prefs.getString("auth", null)
        wallet?.let { refreshWallet(it) }
    }

    /** Called on every resume: a new UTC day starts a fresh puzzle even if the app stayed open. */
    fun checkDay() {
        val now = today()
        if (now == epochDay) return
        epochDay = now
        rounds = roundsFor(now)
        results = loadResults()
        roundIndex = 0
        pendingGuess = null
        sealedSignature = prefs.getString("sealed_$now", null)
        hinted = loadHints()
        board = emptyList()
        screen = Screen.Home
    }

    fun open(target: Screen) {
        screen = target
        if (target == Screen.Board) loadBoard()
    }

    fun play() {
        roundIndex = results.size.coerceAtMost(rounds.size - 1)
        pendingGuess = null
        screen = if (finished) Screen.Summary else Screen.Round
    }

    fun placeGuess(p: LatLon) {
        if (!revealed) pendingGuess = p
    }

    fun submitGuess() {
        val guess = pendingGuess ?: return
        if (revealed) return
        val d = Geo.distanceKm(guess, rounds[roundIndex].location)
        results = results + RoundResult(guess, d, Geo.score(d))
        saveResults()
    }

    fun next() {
        if (!revealed) return
        if (roundIndex + 1 < rounds.size) {
            roundIndex++
            pendingGuess = null
        } else {
            screen = Screen.Summary
        }
    }

    fun shareText(): String = Daily.shareText(puzzle, results.map { it.score }) + "\nGuess the place on Seeker 🌍"

    fun connect(sender: ActivityResultSender) = launchBusy {
        when (val r = connector.connect(sender)) {
            is WalletResult.Ok -> {
                wallet = r.value
                prefs.edit().putString("wallet", r.value).putString("auth", connector.authToken).apply()
                refreshWallet(r.value)
            }
            is WalletResult.Error -> { Log.w(TAG, "wallet error: ${r.message}"); message = r.message }
        }
    }

    fun refreshWallet() { wallet?.let(::refreshWallet) }

    private fun refreshWallet(address: String) {
        Log.i(TAG, "wallet $address")
        balanceError = false
        viewModelScope.launch {
            runCatching { rpc.skrBalance(address) }
                .onSuccess { skrBalance = it }
                .onFailure { balanceError = true; Log.w(TAG, "SKR balance failed", it) }
        }
        // Separate coroutine: the Token-2022 scan can be slow on public RPC and must not hold up the balance.
        viewModelScope.launch {
            runCatching { rpc.genesisMint(address) }
                .onSuccess {
                    genesisMint = it
                    prefs.edit().putString("sgt", it).apply()
                }
                .onFailure { Log.w(TAG, "Genesis lookup failed", it) }
        }
    }

    /** Burns SKR and reveals the continent of the current photo. Counted on the leaderboard. */
    fun hint(sender: ActivityResultSender) = launchBusy {
        if (revealed || roundIndex in hinted || rounds[roundIndex].hintRegion == null) return@launchBusy
        val player = wallet ?: return@launchBusy run { message = "Connect a wallet to buy hints with SKR" }
        val balance = skrBalance
        if (balance != null && balance < Config.HINT_SKR) {
            return@launchBusy run { message = "You need ${Config.HINT_SKR} SKR for a hint (you have %.2f)".format(balance) }
        }
        val round = roundIndex
        val memo = "atlas1-hint|$epochDay|${round + 1}"
        val bh = rpc.latestBlockhash()
        val tx = Transactions.skrBurn(player, Config.HINT_SKR, memo, bh.hash)
        when (val r = connector.signAndSend(sender, tx, bh.slot)) {
            is WalletResult.Ok -> {
                if (!confirmed(r.value)) return@launchBusy
                hinted = hinted + round
                prefs.edit().putLong("hint_day", epochDay).putStringSet("hints", hinted.map { it.toString() }.toSet()).apply()
                refreshWallet(player)
            }
            is WalletResult.Error -> { Log.w(TAG, "wallet error: ${r.message}"); message = r.message }
        }
    }

    fun seal(sender: ActivityResultSender) = launchBusy {
        val player = wallet ?: return@launchBusy run { message = "Connect a wallet first" }
        val memo = Board.memo(epochDay, total, player, genesisMint, hinted.size)
        val bh = rpc.latestBlockhash()
        val tx = Transactions.scoreMemo(player, memo, bh.hash)
        when (val r = connector.signAndSend(sender, tx, bh.slot)) {
            is WalletResult.Ok -> {
                if (!confirmed(r.value)) return@launchBusy
                sealedSignature = r.value
                prefs.edit().putString("sealed_$epochDay", r.value).apply()
                message = "Score sealed on Solana"
                screen = Screen.Board
                loadBoard()
            }
            is WalletResult.Error -> { Log.w(TAG, "wallet error: ${r.message}"); message = r.message }
        }
    }

    fun loadBoard() {
        if (boardJob?.isActive == true) return
        boardJob = viewModelScope.launch {
        runCatching {
            val today = rpc.boardEntries().filter { it.entry.day == epochDay }
                .sortedByDescending { it.entry.score }
                .take(BOARD_VERIFY_LIMIT)
            // A network error on one entry drops only that entry (signer) or its badge (Genesis).
            Board.verify(
                today,
                signerOf = { sig -> runCatching { rpc.feePayer(sig) }.getOrNull() },
                genesisOf = { player -> runCatching { rpc.genesisMint(player) }.getOrNull() },
            )
        }
            .onSuccess { entries ->
                val mine = sealedSignature?.let { wallet?.let { w -> BoardEntry(epochDay, total, w, genesisMint, hinted.size) } }
                board = Board.rank(entries + listOfNotNull(mine), epochDay)
            }
            .onFailure { message = "Leaderboard unavailable: ${it.message}" }
        }
    }

    private suspend fun confirmed(signature: String): Boolean = when (rpc.awaitConfirmation(signature)) {
        true -> true
        false -> { message = "Transaction failed on-chain"; false }
        null -> { message = "Sent, but not confirmed yet. Check your wallet in a minute."; false }
    }

    /** One wallet action at a time: a second tap while the wallet is open is ignored. */
    private fun launchBusy(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try { block() } catch (e: Exception) {
                Log.w(TAG, "wallet action failed", e)
                message = e.message ?: e.toString()
            } finally { busy = false }
        }
    }

    /** Stored guesses only; distance and score are recomputed so corrupt or stale data can't crash or cheat. */
    private fun loadResults(): List<RoundResult> {
        if (prefs.getLong("day", -1) != epochDay) return emptyList()
        val today = rounds
        val guesses = runCatching {
            prefs.getString("results", "").orEmpty().split(";").filter { it.isNotBlank() }.map {
                val parts = it.split(",")
                LatLon(parts[0].toDouble(), parts[1].toDouble())
            }
        }.getOrElse { emptyList() }
        return guesses.take(today.size)
            .filter { it.lat in -90.0..90.0 && it.lon in -180.0..180.0 }
            .mapIndexed { i, g ->
                val d = Geo.distanceKm(g, today[i].location)
                RoundResult(g, d, Geo.score(d))
            }
    }

    private fun loadHints(): Set<Int> =
        if (prefs.getLong("hint_day", -1) != epochDay) emptySet()
        else prefs.getStringSet("hints", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    private fun saveResults() {
        prefs.edit()
            .putLong("day", epochDay)
            .putString("results", results.joinToString(";") { "${it.guess.lat},${it.guess.lon}" })
            .apply()
    }

    private fun roundsFor(day: Long): List<Photo> = Daily.pick(day, pool.size).map { pool[it] }

    private fun today(): Long = LocalDate.now(ZoneOffset.UTC).toEpochDay()
}
