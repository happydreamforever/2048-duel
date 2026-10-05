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

The Gradle wrapper files (`gradlew`, `gradle/wrapper/*`) are part of Gradle (Apache-2.0).
## Bundled assets and libraries

- **"Rubik's Cube" by DatSketch** — 3D model, licensed
  [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/),
  <https://sketchfab.com/3d-models/rubiks-cube-eaba6bf1c7da497f926852006c7bd855>.
  Modified: regrouped into 27 cubie nodes, rescaled, tiles recolored to the WCA scheme,
  tile winding fixed and materials replaced (`tools/cube-model/build_cube_glb.py`).
  Shipped as `android/src/main/assets/models/rubiks_cube.glb`.
- **Google Filament** (Apache-2.0) — the PBR renderer and glTF loader that draw the cube
  (`com.google.android.filament:filament-android` / `gltfio-android`).
