package app.seekeratlas.wallet

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import org.sol4k.Base58

sealed interface WalletResult<out T> {
    data class Ok<T>(val value: T) : WalletResult<T>
    data class Error(val message: String) : WalletResult<Nothing>
}

/** Thin wrapper over Mobile Wallet Adapter: connect, then sign-and-send prepared transactions. */
class WalletConnector {
    private val adapter = MobileWalletAdapter(
        ConnectionIdentity(
            identityUri = Uri.parse("https://seekeratlas.app"),
            iconUri = Uri.parse("favicon.ico"),
            identityName = "Seeker Atlas",
        )
    ).apply { blockchain = Solana.Mainnet }

    var authToken: String?
        get() = adapter.authToken
        set(value) { adapter.authToken = value }

    suspend fun connect(sender: ActivityResultSender): WalletResult<String> =
        when (val r = adapter.connect(sender)) {
            is TransactionResult.Success -> WalletResult.Ok(Base58.encode(r.authResult.accounts.first().publicKey))
            is TransactionResult.NoWalletFound -> WalletResult.Error("No Solana wallet found. Install Phantom, Solflare or use Seed Vault.")
            is TransactionResult.Failure -> WalletResult.Error(r.message)
        }

    suspend fun signAndSend(sender: ActivityResultSender, tx: ByteArray): WalletResult<String> {
        val r = adapter.transact(sender) { signAndSendTransactions(arrayOf(tx)) }
        return when (r) {
            is TransactionResult.Success -> WalletResult.Ok(Base58.encode(r.payload.signatures.first()))
            is TransactionResult.NoWalletFound -> WalletResult.Error("No Solana wallet found.")
            is TransactionResult.Failure -> WalletResult.Error(r.message)
        }
    }
}
