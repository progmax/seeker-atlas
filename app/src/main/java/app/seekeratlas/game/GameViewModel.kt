package app.seekeratlas.game

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

private const val BOARD_VERIFY_LIMIT = 25

enum class Screen { Home, Round, Summary, Board }

data class RoundResult(val guess: LatLon, val distanceKm: Double, val score: Int)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("atlas", Context.MODE_PRIVATE)
    private val rpc = SolanaRpc()
    private val connector = WalletConnector()

    val epochDay: Long = LocalDate.now(ZoneOffset.UTC).toEpochDay()
    val puzzle: Int = Daily.puzzleNumber(epochDay)
    private val pool = Photos.load(app)
    val rounds: List<Photo> = Daily.pick(epochDay, pool.size).map { pool[it] }

    var screen by mutableStateOf(Screen.Home); private set
    var roundIndex by mutableStateOf(0); private set
    var pendingGuess by mutableStateOf<LatLon?>(null); private set
    var results by mutableStateOf(loadResults()); private set

    var wallet by mutableStateOf(prefs.getString("wallet", null)); private set
    var genesisMint by mutableStateOf(prefs.getString("sgt", null)); private set
    var skrBalance by mutableStateOf<Double?>(null); private set
    var board by mutableStateOf<List<BoardEntry>>(emptyList()); private set
    var sealedSignature by mutableStateOf(prefs.getString("sealed_$epochDay", null)); private set
    var tipped by mutableStateOf(prefs.getStringSet("tipped", emptySet())!!.toSet()); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)

    val finished: Boolean get() = results.size >= rounds.size
    val total: Int get() = results.sumOf { it.score }
    val revealed: Boolean get() = results.size > roundIndex

    init {
        connector.authToken = prefs.getString("auth", null)
        wallet?.let { refreshWallet(it) }
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
            is WalletResult.Error -> message = r.message
        }
    }

    private fun refreshWallet(address: String) = viewModelScope.launch {
        runCatching { rpc.skrBalance(address) }.onSuccess { skrBalance = it }
        runCatching { rpc.genesisMint(address) }.onSuccess {
            genesisMint = it
            prefs.edit().putString("sgt", it).apply()
        }
    }

    fun tip(sender: ActivityResultSender, photo: Photo) = launchBusy {
        val from = wallet ?: return@launchBusy run { message = "Connect a wallet first" }
        val to = photo.wallet ?: Config.TREASURY_ADDRESS
        val tx = Transactions.skrTip(from, to, Config.TIP_SKR, rpc.latestBlockhash())
        when (val r = connector.signAndSend(sender, tx)) {
            is WalletResult.Ok -> {
                tipped = tipped + photo.id
                prefs.edit().putStringSet("tipped", tipped).apply()
                message = "Sent ${Config.TIP_SKR} SKR to ${if (photo.wallet != null) "the photographer" else "the Atlas photo fund"}"
                refreshWallet(from)
            }
            is WalletResult.Error -> message = r.message
        }
    }

    fun seal(sender: ActivityResultSender) = launchBusy {
        val player = wallet ?: return@launchBusy run { message = "Connect a wallet first" }
        val memo = Board.memo(epochDay, total, player, genesisMint)
        val tx = Transactions.scoreMemo(player, memo, rpc.latestBlockhash())
        when (val r = connector.signAndSend(sender, tx)) {
            is WalletResult.Ok -> {
                sealedSignature = r.value
                prefs.edit().putString("sealed_$epochDay", r.value).apply()
                message = "Score sealed on Solana"
                screen = Screen.Board
                loadBoard()
            }
            is WalletResult.Error -> message = r.message
        }
    }

    fun loadBoard() = viewModelScope.launch {
        runCatching {
            val today = rpc.boardEntries().filter { it.entry.day == epochDay }
                .sortedByDescending { it.entry.score }
                .take(BOARD_VERIFY_LIMIT)
            Board.verify(today, signerOf = { rpc.feePayer(it) }, genesisOf = { rpc.genesisMint(it) })
        }
            .onSuccess { entries ->
                val mine = sealedSignature?.let { wallet?.let { w -> BoardEntry(epochDay, total, w, genesisMint) } }
                board = Board.rank(entries + listOfNotNull(mine), epochDay)
            }
            .onFailure { message = "Leaderboard unavailable: ${it.message}" }
    }

    private fun launchBusy(block: suspend () -> Unit) = viewModelScope.launch {
        busy = true
        try { block() } catch (e: Exception) { message = e.message ?: e.toString() } finally { busy = false }
    }

    private fun loadResults(): List<RoundResult> {
        if (prefs.getLong("day", -1) != epochDay) return emptyList()
        return prefs.getString("results", "")!!.split(";").filter { it.isNotBlank() }.map {
            val (lat, lon, d, s) = it.split(",")
            RoundResult(LatLon(lat.toDouble(), lon.toDouble()), d.toDouble(), s.toInt())
        }
    }

    private fun saveResults() {
        prefs.edit()
            .putLong("day", epochDay)
            .putString("results", results.joinToString(";") { "${it.guess.lat},${it.guess.lon},${it.distanceKm},${it.score}" })
            .apply()
    }
}
