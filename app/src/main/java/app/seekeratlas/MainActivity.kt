package app.seekeratlas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import app.seekeratlas.game.GameViewModel
import app.seekeratlas.game.Screen
import app.seekeratlas.ui.AtlasApp
import app.seekeratlas.ui.Mint
import app.seekeratlas.ui.Violet
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        vm.checkDay()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sender = ActivityResultSender(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Mint, secondary = Violet)) {
                BackHandler(enabled = vm.screen != Screen.Home) { vm.open(Screen.Home) }
                AtlasApp(vm, sender)
            }
        }
    }
}
