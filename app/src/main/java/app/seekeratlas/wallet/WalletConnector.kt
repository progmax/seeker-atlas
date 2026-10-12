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
            identityUri = Uri.parse("https://seeker-atlas-omega.vercel.app"),
            iconUri = Uri.parse("icon.png"),
            identityName = "Seeker Atlas",
        ),
        // The player may read the transaction in the wallet for a while; the library default is too short.
        timeout = 180_000,
    ).apply { blockchain = Solana.Mainnet }

    var authToken: String?
        get() = adapter.authToken
        set(value) { adapter.authToken = value }

    /** First connection: always a fresh authorize. */
    suspend fun connect(sender: ActivityResultSender): WalletResult<String> {
        adapter.authToken = null
        return when (val r = adapter.connect(sender)) {
            is TransactionResult.Success -> WalletResult.Ok(Base58.encode(r.authResult.accounts.first().publicKey))
            is TransactionResult.NoWalletFound -> WalletResult.Error("No Solana wallet found. Install Phantom, Solflare or use Seed Vault.")
            is TransactionResult.Failure -> WalletResult.Error(r.message)
        }
    }

    data class Sent(val signature: String, val address: String)

    /**
     * One wallet visit: authorize, then sign and send the transaction built for the authorized address.
     * Phantom refuses to reauthorize this app's saved token, so a fresh authorize keeps it to a single
     * Connect + Confirm instead of a separate Connect step beforehand.
     * [minContextSlot] is mandatory for Phantom: without it the wallet silently drops the request.
     */
    suspend fun signAndSend(
        sender: ActivityResultSender,
        minContextSlot: Long,
        build: (address: String) -> ByteArray,
    ): WalletResult<Sent> {
        val params = TransactionParams(
            minContextSlot = minContextSlot.toInt(),
            commitment = "confirmed",
            skipPreflight = false,
            maxRetries = null,
            waitForCommitmentToSendNextTransaction = false,
        )
        adapter.authToken = null
        val r = adapter.transact(sender) { auth ->
            val address = Base58.encode(auth.accounts.first().publicKey)
            val sig = signAndSendTransactions(arrayOf(build(address)), params).signatures.first()
            Sent(Base58.encode(sig), address)
        }
        return when (r) {
            is TransactionResult.Success -> WalletResult.Ok(r.payload)
            is TransactionResult.NoWalletFound -> WalletResult.Error("No Solana wallet found. Install Phantom, Solflare or use Seed Vault.")
            is TransactionResult.Failure -> {
                android.util.Log.w("SeekerAtlas", "MWA failure: ${r.message}", r.e)
                WalletResult.Error(r.message)
            }
        }
    }

}
