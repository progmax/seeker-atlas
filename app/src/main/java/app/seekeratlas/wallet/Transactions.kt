package app.seekeratlas.wallet

import app.seekeratlas.Config
import org.sol4k.AccountMeta
import org.sol4k.Base58
import org.sol4k.Constants.ASSOCIATED_TOKEN_PROGRAM_ID
import org.sol4k.Constants.SYSTEM_PROGRAM
import org.sol4k.Constants.TOKEN_PROGRAM_ID
import org.sol4k.PublicKey
import org.sol4k.Transaction
import org.sol4k.instruction.Instruction
import org.sol4k.instruction.SplTransferInstruction

/** Builds unsigned legacy transactions for the wallet to sign and send via Mobile Wallet Adapter. */
object Transactions {

    fun skrAta(owner: String): String =
        PublicKey.findProgramDerivedAddress(PublicKey(owner), PublicKey(Config.SKR_MINT)).publicKey.toBase58()

    fun skrTip(from: String, to: String, amountSkr: Long, blockhash: String): ByteArray {
        val owner = PublicKey(from)
        val recipient = PublicKey(to)
        val mint = PublicKey(Config.SKR_MINT)
        val fromAta = PublicKey.findProgramDerivedAddress(owner, mint).publicKey
        val toAta = PublicKey.findProgramDerivedAddress(recipient, mint).publicKey
        val raw = amountSkr * POW10[Config.SKR_DECIMALS]
        return unsigned(
            blockhash, owner,
            CreateAtaIdempotent(payer = owner, ata = toAta, owner = recipient, mint = mint),
            SplTransferInstruction(fromAta, toAta, mint, owner, raw, Config.SKR_DECIMALS),
        )
    }

    fun scoreMemo(player: String, memo: String, blockhash: String): ByteArray =
        unsigned(blockhash, PublicKey(player), Memo(memo, PublicKey(Config.BOARD_ADDRESS)))

    private fun unsigned(blockhash: String, feePayer: PublicKey, vararg ix: Instruction): ByteArray =
        Transaction(blockhash, ix.toList(), feePayer)
            .apply { addSignature(Base58.encode(ByteArray(64))) }
            .serialize()

    private val POW10 = LongArray(10) { var v = 1L; repeat(it) { v *= 10 }; v }

    private class CreateAtaIdempotent(payer: PublicKey, ata: PublicKey, owner: PublicKey, mint: PublicKey) : Instruction {
        override val data = byteArrayOf(1)
        override val keys = listOf(
            AccountMeta.signerAndWritable(payer),
            AccountMeta.writable(ata),
            AccountMeta(owner),
            AccountMeta(mint),
            AccountMeta(SYSTEM_PROGRAM),
            AccountMeta(TOKEN_PROGRAM_ID),
        )
        override val programId = ASSOCIATED_TOKEN_PROGRAM_ID
    }

    private class Memo(text: String, board: PublicKey) : Instruction {
        override val data = text.encodeToByteArray()
        override val keys = listOf(AccountMeta(board))
        override val programId = PublicKey(Config.MEMO_PROGRAM)
    }
}
