package app.seekeratlas.wallet

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionParams
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
            identityUri = Uri.parse("https://progmax.github.io"),
            iconUri = Uri.parse("icon.png"),
            identityName = "Seeker Atlas",
        ),
        // The player may read the transaction in the wallet for a while; the library default is too short.
        timeout = 180_000,
    ).apply { blockchain = Solana.Mainnet }

    var authToken: String?
        get() = adapter.authToken
        set(value) { adapter.authToken = value }

    suspend fun connect(sender: ActivityResultSender): WalletResult<String> =
        when (val r = withFreshAuthOnFailure { adapter.connect(sender) }) {
            is TransactionResult.Success -> WalletResult.Ok(Base58.encode(r.authResult.accounts.first().publicKey))
            is TransactionResult.NoWalletFound -> WalletResult.Error("No Solana wallet found. Install Phantom, Solflare or use Seed Vault.")
            is TransactionResult.Failure -> WalletResult.Error(r.message)
        }

    /** [minContextSlot] is mandatory for Phantom: without it the wallet silently drops the request. */
    suspend fun signAndSend(sender: ActivityResultSender, tx: ByteArray, minContextSlot: Long): WalletResult<String> {
        val params = TransactionParams(
            minContextSlot = minContextSlot.toInt(),
            commitment = "confirmed",
            skipPreflight = false,
            maxRetries = null,
            waitForCommitmentToSendNextTransaction = false,
        )
        val r = withFreshAuthOnFailure { adapter.transact(sender) { signAndSendTransactions(arrayOf(tx), params) } }
        return when (r) {
            is TransactionResult.Success -> WalletResult.Ok(Base58.encode(r.payload.signatures.first()))
            is TransactionResult.NoWalletFound -> WalletResult.Error("No Solana wallet found.")
            is TransactionResult.Failure -> {
                android.util.Log.w("SeekerAtlas", "MWA failure: ${r.message}", r.e)
                WalletResult.Error(r.message)
            }
        }
    }

    /**
     * Phantom rejects reauthorize with a token from an earlier session ("authorization request failed"),
     * which used to cost the player a second prompt. Every action authorizes fresh in one wallet session.
     */
    private suspend fun <T> withFreshAuthOnFailure(block: suspend () -> TransactionResult<T>): TransactionResult<T> {
        adapter.authToken = null
        return block()
    }
}
