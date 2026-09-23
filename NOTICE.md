# Third-party notices

This project was written from scratch, but the following open-source projects were
studied and some of their ideas or small code patterns were adapted:

- **alexjlockwood/compose-multiplatform-2048** (MIT) — the pattern of keying each tile
  composable by a stable tile id and animating it from its previous cell, and of keeping
  merged-away tiles in the render list for one frame so they slide under the new tile.
  Adapted in `android/.../ui/components/BoardView.kt`, `TileView.kt` and `game/BoardUi.kt`.
- **2048-LRU/2048** (MIT) — Material 3 theming structure and the swipe-detection
  approach (`ui/input/Swipe.kt`).
- **vishal2376/mini2048** (MIT) — haptic feedback on moves.
- **es/2048-multiplayer** (MIT) — FIFO matchmaking queue and identical start boards
  (`server/.../Lobby.kt`, `Match.kt`).
- **2048-BattleRoyale/2048royale-server** (no license; ideas only) — server-authoritative
  board handling and "eliminated when no tile can spawn".
- **harinarayanan2005/2048_NEON** (no license; ideas only) — neon tile palette, particle
  burst + shock ring effect, board micro-shake, combo pill and procedural Web Audio sound.
  Reimplemented for Compose/Android in `Particles.kt`, `BoardFx.kt` and `fx/SoundFx.kt`.
- **gabrielecirulli/2048** (MIT) — the original game rules.
- **cjurjiu/AnimCubeAndroid** (Apache-2.0) — vendored as the `:cube2` module (AnimCube 3D
  renderer). See `cube2/ATTRIBUTION.md`.

The Gradle wrapper files (`gradlew`, `gradle/wrapper/*`) are part of Gradle (Apache-2.0).
