package app.seekeratlas.wallet

import app.seekeratlas.Config
import org.sol4k.AccountMeta
import org.sol4k.Base58
import org.sol4k.Constants.TOKEN_PROGRAM_ID
import org.sol4k.PublicKey
import org.sol4k.Transaction
import org.sol4k.instruction.Instruction
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds unsigned legacy transactions for the wallet to sign and send via Mobile Wallet Adapter. */
object Transactions {

    fun skrAta(owner: String): String =
        PublicKey.findProgramDerivedAddress(PublicKey(owner), PublicKey(Config.SKR_MINT)).publicKey.toBase58()

    /** Burns SKR from the player's associated token account, with a memo saying what it paid for. */
    fun skrBurn(owner: String, amountSkr: Long, memo: String, blockhash: String): ByteArray {
        val ownerKey = PublicKey(owner)
        val mint = PublicKey(Config.SKR_MINT)
        val ata = PublicKey.findProgramDerivedAddress(ownerKey, mint).publicKey
        val raw = amountSkr * POW10[Config.SKR_DECIMALS]
        return unsigned(
            blockhash, ownerKey,
            BurnChecked(ata, mint, ownerKey, raw, Config.SKR_DECIMALS),
            Memo(memo, null),
        )
    }

    fun scoreMemo(player: String, memo: String, blockhash: String): ByteArray =
        unsigned(blockhash, PublicKey(player), Memo(memo, PublicKey(Config.BOARD_ADDRESS)))

    private fun unsigned(blockhash: String, feePayer: PublicKey, vararg ix: Instruction): ByteArray =
        Transaction(blockhash, ix.toList(), feePayer)
            .apply { addSignature(Base58.encode(ByteArray(64))) }
            .serialize()

    private val POW10 = LongArray(10) { var v = 1L; repeat(it) { v *= 10 }; v }

    /** SPL Token BurnChecked (instruction 15): [account w, mint w, owner signer], amount u64 LE, decimals u8. */
    internal class BurnChecked(account: PublicKey, mint: PublicKey, owner: PublicKey, amount: Long, decimals: Int) : Instruction {
        override val data: ByteArray = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
            .put(15).putLong(amount).put(decimals.toByte()).array()
        override val keys = listOf(AccountMeta.writable(account), AccountMeta.writable(mint), AccountMeta.signer(owner))
        override val programId = TOKEN_PROGRAM_ID
    }

    private class Memo(text: String, board: PublicKey?) : Instruction {
        override val data = text.encodeToByteArray()
        override val keys = listOfNotNull(board?.let { AccountMeta(it) })
        override val programId = PublicKey(Config.MEMO_PROGRAM)
    }
}
