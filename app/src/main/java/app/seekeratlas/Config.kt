package app.seekeratlas

object Config {
    const val RPC_URL = "https://api.mainnet-beta.solana.com"
    const val SKR_MINT = "SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3"
    const val SKR_DECIMALS = 6
    const val TIP_SKR = 5L
    /** Seeker Genesis Token collection (Token-2022 group mint). */
    const val SGT_GROUP = "GT22s89nU4iWFkNXj1Bw6uYhJJWDRPpShHt4Bk8f99Te"
    const val MEMO_PROGRAM = "Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo"
    /** Read-only marker account referenced by every score memo; the leaderboard is its transaction history. */
    const val BOARD_ADDRESS = "8b9sg2u8VhEgjPoHv9SjVRuyfcnuhrqkX8SfREEZHrzj"
    /** Receives tips for photos whose author has no wallet yet (Wikimedia seed set). */
    const val TREASURY_ADDRESS = "TREASURY_NOT_SET"
}
