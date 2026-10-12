package app.seekeratlas.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.seekeratlas.Config
import app.seekeratlas.game.Daily
import app.seekeratlas.game.GameViewModel
import app.seekeratlas.game.Photo
import app.seekeratlas.game.Screen
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlin.math.roundToInt

private val Bg = Color(0xFF0B0F1A)
private val Card = Color(0xFF151B2B)
private val Muted = Color(0xFF8A94A8)

@Composable
fun AtlasApp(vm: GameViewModel, sender: ActivityResultSender) {
    Box(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
        when (vm.screen) {
            Screen.Home -> HomeScreen(vm, sender)
            Screen.Round -> RoundScreen(vm, sender)
            Screen.Summary -> SummaryScreen(vm, sender)
            Screen.Board -> BoardScreen(vm)
        }
        vm.message?.let { msg ->
            Box(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)).background(Card).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = { vm.message = null }) { Text("OK", color = Mint) }
                }
            }
        }
        if (vm.busy) {
            Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Mint)
            }
        }
    }
}

@Composable
private fun HomeScreen(vm: GameViewModel, sender: ActivityResultSender) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text("SEEKER ATLAS", color = Mint, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        Text("Daily #${vm.puzzle} · 5 places · one try", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Look at the photo. Tap where on Earth you think it was taken. The closer, the more points.",
            color = Color.White, textAlign = TextAlign.Center, fontSize = 16.sp,
        )
        Spacer(Modifier.weight(1f))
        WalletCard(vm, sender)
        val label = when {
            vm.finished -> "See today's result · ${vm.total}"
            vm.results.isNotEmpty() -> "Continue (${vm.results.size}/${vm.rounds.size})"
            else -> "Play today's Atlas"
        }
        PrimaryButton(label) { vm.play() }
        OutlinedButton(onClick = { vm.open(Screen.Board) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Today's leaderboard", color = Color.White)
        }
    }
}

@Composable
private fun WalletCard(vm: GameViewModel, sender: ActivityResultSender) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val w = vm.wallet
        if (w == null) {
            Text("Connect a wallet to seal your score on Solana and buy hints with SKR.", color = Muted, fontSize = 14.sp)
            Button(onClick = { vm.connect(sender) }, colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                Text("Connect wallet")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(short(w), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (vm.genesisMint != null) Badge("✓ Verified Seeker", Mint) else Badge("Unverified", Muted)
            }
            Text(
                vm.skrBalance?.let { "%.2f SKR".format(it) }
                    ?: if (vm.balanceError) "Balance unavailable · tap to retry" else "Loading balance…",
                color = if (vm.balanceError) Mint else Muted, fontSize = 14.sp,
                modifier = Modifier.clickable(enabled = vm.balanceError) { vm.refreshWallet() },
            )
            if (vm.genesisMint == null) {
                Text("Your score counts on the board once per Seeker Genesis Token.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RoundScreen(vm: GameViewModel, sender: ActivityResultSender) {
    val photo = vm.rounds[vm.roundIndex]
    val result = vm.results.getOrNull(vm.roundIndex)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Round ${vm.roundIndex + 1}/${vm.rounds.size}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${vm.total} pts", color = Mint, fontWeight = FontWeight.Bold)
        }
        PhotoView(photo, revealed = result != null, modifier = Modifier.fillMaxWidth().weight(1f))
        val hintRegion = if (vm.roundIndex in vm.hinted) photo.hintRegion else null
        Box(Modifier.fillMaxWidth().weight(1f)) {
            WorldMap(
                guess = result?.guess ?: vm.pendingGuess,
                answer = if (result != null) photo.location else null,
                hint = if (result == null) hintRegion else null,
                onTap = vm::placeGuess,
                modifier = Modifier.fillMaxSize(),
            )
            if (hintRegion != null && result == null) {
                Badge("💡 Somewhere in ${hintRegion.name}", Mint, Modifier.align(Alignment.TopStart).padding(8.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (result == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val hintUsed = vm.roundIndex in vm.hinted
                    OutlinedButton(
                        onClick = { vm.hint(sender) },
                        enabled = !hintUsed && photo.hintRegion != null,
                        modifier = Modifier.height(54.dp),
                    ) { Text(if (hintUsed) "💡 Used" else "💡 ${Config.HINT_SKR} SKR", color = if (hintUsed) Muted else Mint) }
                    Box(Modifier.weight(1f)) {
                        PrimaryButton(if (vm.pendingGuess == null) "Tap the map" else "Guess", enabled = vm.pendingGuess != null) {
                            vm.submitGuess()
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${photo.place}, ${photo.country}", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${formatKm(result.distanceKm)} away · +${result.score}" + if (vm.roundIndex in vm.hinted) " · 💡" else "", color = Mint)
                    }
                }
                PrimaryButton(if (vm.roundIndex + 1 < vm.rounds.size) "Next place" else "See results") { vm.next() }
            }
        }
    }
}

@Composable
private fun PhotoView(photo: Photo, revealed: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember(photo.file) {
        context.assets.open(photo.file).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }
    Box(modifier.background(Color.Black)) {
        Image(bitmap, contentDescription = "Where is this?", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        // Author and source page make the photo searchable, so credits appear with the answer.
        if (revealed) {
            Text(
                "© ${photo.author} · ${photo.license} · resized · source ↗",
                color = Color.White, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd).background(Color(0x99000000))
                    .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(photo.source))) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SummaryScreen(vm: GameViewModel, sender: ActivityResultSender) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Atlas #${vm.puzzle}", color = Muted, fontSize = 16.sp)
        Text("${vm.total}", color = Mint, fontSize = 56.sp, fontWeight = FontWeight.Black)
        Text("of ${Daily.ROUNDS * 5000}", color = Muted)
        vm.rounds.zip(vm.results).forEach { (photo, r) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(12.dp)) {
                Text("${photo.place}, ${photo.country}", color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)
                Text("${formatKm(r.distanceKm)} · ${r.score}", color = Mint, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Share result") {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, vm.shareText())
            context.startActivity(Intent.createChooser(send, "Share your Atlas"))
        }
        when {
            vm.sealedSignature != null -> OutlinedButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://solscan.io/tx/${vm.sealedSignature}"))) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Sealed on Solana ✓ · view tx", color = Mint) }
            else -> OutlinedButton(onClick = { vm.seal(sender) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Seal score on Solana", color = Color.White)
            }
        }
        Row {
            TextButton(onClick = { vm.open(Screen.Board) }) { Text("Leaderboard", color = Muted) }
            TextButton(onClick = { vm.open(Screen.Home) }) { Text("Home", color = Muted) }
        }
    }
}

@Composable
private fun BoardScreen(vm: GameViewModel) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Atlas #${vm.puzzle} leaderboard", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.open(Screen.Home) }) { Text("Close", color = Muted) }
        }
        Text("Scores are memo transactions on Solana. One entry per Seeker Genesis Token.", color = Muted, fontSize = 13.sp)
        if (vm.board.isEmpty()) {
            Text("No sealed scores yet today. Be the first.", color = Muted, modifier = Modifier.padding(top = 24.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(vm.board) { i, e ->
                val mine = e.player == vm.wallet
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (mine) Color(0xFF1E2A3F) else Card).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", color = Muted, modifier = Modifier.padding(end = 12.dp))
                    Text(short(e.player) + if (mine) " (you)" else "", color = Color.White, modifier = Modifier.weight(1f))
                    if (e.hints > 0) Text("💡${e.hints}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
                    if (e.verified) Badge("Seeker", Mint)
                    Text("${e.score}", color = Mint, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color.Black),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
private fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xCC0B0F1A)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private fun short(address: String) = address.take(4) + "…" + address.takeLast(4)

private fun formatKm(km: Double): String = when {
    km < 1 -> "${(km * 1000).roundToInt()} m"
    km < 100 -> "%.1f km".format(km)
    else -> "${km.roundToInt()} km"
}
