# Seeker Atlas

**A daily geo-guessing game for Solana Seeker.** Five photos a day, the same for everyone. Tap where on Earth you think each one was taken; the closer you are, the more points. Share your result as spoiler-free squares, seal it on Solana, and see how you rank against other Seekers.

Built for **CLOCK IN — a Solana Mobile hackathon**.

```
Seeker Atlas #3 — 18450/25000
🟩🟩🟨🟩🟧
```

## What's on-chain

| Feature | How it works |
|---|---|
| **Wallet** | Mobile Wallet Adapter (mainnet). Works with Seed Vault, Phantom, Solflare. |
| **Seal your score** | A memo transaction `atlas1\|day\|score\|player\|genesisMint\|hints` that references a fixed board account. No backend: the leaderboard *is* that account's transaction history (`getSignaturesForAddress`). |
| **Verified Seeker** | The app checks the wallet for a **Seeker Genesis Token** (Token-2022 member of group `GT22s89n…f99Te`). Verified players get a badge, and the board keeps **one entry per Genesis Token**. |
| **Anti-spoofing** | Memo text is self-reported, so the board only trusts it as far as the chain backs it: the fee payer must be the claimed player, and a claimed Genesis Token must really be in that wallet. |
| **SKR hint** | Stuck? Burn **5 SKR** (`BurnChecked`) to highlight the photo's continent. SKR leaves circulation — nobody, including us, receives it. Hints used are shown next to your score. |

## Game design

- **Daily puzzle, like Wordle.** One shared set per UTC day creates a reason to come back tomorrow and something to compare with friends.
- **150 photos from 125 countries**, rotated without repeats for 30 days. Credits are hidden until you guess (the source page names the place) and shown in full afterwards and in [CREDITS.md](CREDITS.md).
- **Scoring:** `5000 · e^(−km/1500)` per photo — ~4970 at 10 km, ~3580 at 500 km, ~180 at 5000 km.
- **Pinch-zoom world map** (equirectangular, CC0), tap to guess, dashed line to the answer.

## Roadmap

The bundled set is a cold start. The real fix for both content and cheating (any public photo can be reverse-image-searched) is **photos taken by Seeker owners**: shot in-app, geotagged by the phone, never published elsewhere — and their authors earn SKR when their photo is picked for a daily set.

## Build

Requirements: JDK 17, Android SDK 37.

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Mainnet transaction simulations (no funds moved) are opt-in:

```bash
ATLAS_NETWORK_TESTS=1 ./gradlew :app:testDebugUnitTest --tests '*NetworkSimulationTest*'
```

Stack: Kotlin 2.4, Jetpack Compose, `mobile-wallet-adapter-clientlib-ktx` 2.2.0, `sol4k` for transaction building, plain JSON-RPC.

## License

Code: MIT. Photos: their own licenses, see [CREDITS.md](CREDITS.md).
