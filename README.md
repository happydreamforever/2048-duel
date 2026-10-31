# 2048 Duel

An Android game hub for **2048, Cube, Math, Word Quiz, and Word Puzzle**, with an authoritative Kotlin server.

Both players get the same seeded board. Merges charge an **attack meter**; every
12 energy launches a **garbage tile** onto the opponent's board. Garbage slides but never
merges, and shatters after two merges next to it. You win when the opponent's board
fills up, or by score when the clock runs out. If nobody is online a server bot fills in,
so the game is always playable.

```
┌──────────────┐    WebSocket (JSON)    ┌──────────────┐
│ android/     │ ◄────────────────────► │ server/      │
│ Jetpack      │  moves, acks, garbage  │ Ktor + Netty │
│ Compose UI   │                        │ matchmaking  │
└──────┬───────┘                        └──────┬───────┘
       │            shared/ (pure Kotlin)       │
       └── GameEngine · Rules · Bot · Protocol ─┘
```

| Module | What it is |
|---|---|
| `shared/` | Deterministic game engine (SplitMix64 RNG, attack energy, garbage tiles), heuristic bot, and the WebSocket message types. Used by both sides, so the client predicts moves instantly and the server verifies them. |
| `server/` | Ktor server: accounts and match history in MySQL (HikariCP + JDBC), FIFO matchmaking with bot fallback, authoritative match loop, countdown/timer, `/stats` and `/leaderboard` endpoints. |
| `android/` | Jetpack Compose client with Play, Community, and Profile pages, online matches, offline training, and solo play. |

## Navigation and online state

- **Play** offers five games: 2048, Cube, Math, Word Quiz, and Word Puzzle.
- **Community** contains Watch rooms, player search, and rankings.
- **Profile** contains account access, coins, the shop, settings, and feedback.

The neon palette, animated ambient background, glowing gradient controls, and shimmer accents accompany the compact typography and new page structure.

Every screen shares one compact floating top toolbar. Its navigation menu opens Play,
Community, Watch, rankings, and Profile; notification and settings buttons remain in the
same place. The toolbar stays outside scrolling content. Smaller typography preserves
Android font scaling, while buttons retain accessible touch targets. Non-game pages and
settings scroll on small displays. Match navigation is guarded while a paid room is active.

Notifications show published SQL announcements and server notices. The unread marker
uses update timestamps and is remembered for each configured server. Contact support lives
under **Settings → Help and support** as a private report form, with validation, draft
preservation during login, sending status, and confirmation. Connection configuration is
under the expandable **Connection settings** section, rather than on the sign-in page.

There is no lobby/community chat. A **Match chat** button appears in the top toolbar only
inside an active PvP competition. Both players can select preset sentences or emoji; there
is no free-text composer. The server authenticates both sending and reading, checks the
actual room membership and PvP mode, and rejects spectators, Bot/demo rooms, waiting or
finished rooms, and arbitrary text. Chat automatically closes when the competition ends.

Saved-game browsing and local progress backups have been removed. Online matches use
server-owned checkpoints to keep players synchronized. A brief connection interruption
can automatically resume the same match without another entry fee. Online cube/2048
sessions have a 90-second reconnect window within the same server process. If that server
restarts, interrupted cube/2048 entries are refunded; minigame rooms restore their board,
players, found words, and absolute timer from SQL. Back up the SQL database and DATA_DIR.

Protocol version remains 7. Android **2.0.0 (11)** updates the UI without requiring a server change.

```powershell
.\gradlew.bat :shared:test :server:test :android:assembleDebug
.\gradlew.bat -Pduel2048.serverOnly :server:installDist
```

Debug APK: `android/build/outputs/apk/debug/android-debug.apk`.
For signed releases, keep your existing keystore configured and run `.\gradlew.bat :android:assembleRelease`.

## Play a specific player

All five games support private PvP invitations. In a game’s mode selector choose
**Play a specific player**, enter the exact opponent account name, and send the invitation.
The **Invitations** button in the floating top bar shows incoming invitations and accepted
matches waiting for you. You can also choose a game and invite directly from that page.
The invitation screen displays your own account name so another player can find you.

The invited player accepts or declines. After acceptance **both players tap Join match**.
The room starts only for that pair; strangers, public queues, and Bot fallback cannot fill
its seats. Each player pays the existing 10-coin PvP entry fee only when both join. Pending
and accepted invitations expire after two minutes and can be cancelled before starting.
A short private minigame countdown hides the question until play starts. Both competitors
receive the same board/question and level, and normal PvP chat and rewards apply.

Private competitions are omitted from public Watch listings, and private minigame payloads
require a participant’s token. Reconnecting resumes the same active 2048/Cube match without
another fee. **Play again** prepares another invitation to the same opponent instead of
queueing against a random player. Accepted or active 2048/Cube invitations expire safely after a
server restart; active minigame rooms retain their state and timer. New invitations may be
sent after the interrupted match has been refunded by the existing recovery flow.

Production invitations persist in the shared SQL database in `pvp_challenges`, created by
the updated game server. `DB=json` uses an atomic local file for development. Update the
APK and game server together; this feature does not require an admin-client update.

## Math and word datasets

The APK contains only the small offline **training** catalog in
`shared/src/main/resources/datasets/training/`: 12 math examples per difficulty and
8 English words per length (48 total) (3–8 letters). Training works without a server.

Every real math/word match, including **vs Bot**, requires a connection. The server
randomly selects from its full catalog in `server/src/main/resources/datasets/matches/`
(80 math problems per difficulty and 24 words per length (144 total)). Full match CSVs are packaged
only in the server distribution. They are not included in the APK or downloaded as a dataset.
Only the chosen problem, shuffled answer options or letter bank, clue, timer, and opaque
puzzle ID are sent. The correct answer stays on the server until the match ends.

Math uses four selectable answers and an explicit Submit button. Word Quiz uses tappable
letter tiles, an assembled-word preview, Undo, and Clear; repeated letters require distinct
tiles. Quiz levels describe word length rather than grid dimensions. Each competitor has
one submission; a wrong answer locks their input while the opponent continues. A server bot
solves after half the time limit, so the player must answer before then. Training offers hints.
**Word Puzzle** is a separate timed competition with 4×4, 6×6, or 8×8 grids. Tap
consecutive horizontal or vertical neighbors to make 3–8 letter dictionary words.
Diagonals, wrapping across row edges, and reusing a cell within a word are forbidden.
Selected cells show their order; tap the last cell or Undo to backtrack, or Clear to restart.
Each distinct valid word earns one point per letter per player. Both PvP players use the
same board, may independently find the same words, and compete for the highest score
at the end of a 120-second round; equal scores draw. Invalid and duplicate submissions
have no penalty and award no points. Bot opponents find a limited set of valid words
over time. Training uses the small offline dictionary; both PvP and Bot use the full
server-only CSV dictionary. Grids, timers, participants, and found words survive server
restarts. Players see their own found words and both scores, while the list of possible
words is revealed only when the round ends. Existing `word` rooms and `WORD` saves
remain Word Quiz; grid games use `word_puzzle` and `WORD_PUZZLE`.

The problem, option order, and partial word selection survive saving and reconnecting.

Math CSV columns: `id,prompt,answer,option_1,option_2,option_3,option_4`.
Word CSV columns: `word,clue`. Keep words uppercase and in the file matching their length.
CSV fields containing commas must be quoted; embedded quotes are doubled. Rebuild the server
after editing full datasets. Editing the training samples requires rebuilding the APK.

## Online synchronization, Watch demos, and coins

The client no longer offers Saved games, Continue, or importing device saves. Online
players reconnect automatically; match state and timers are owned by the game server.
MySQL/MariaDB deployments write online checkpoints, all five competition room types,
wallets, entries, rewards, and purchase requests into the same SQL database used by admin.
Apply the admin migrations with `npm run db:migrate` and refresh datasets with `npm run db:seed`.
The game also creates the compatible runtime tables on startup. Existing file-based
wallet balances are imported only when a SQL wallet does not already exist.
`DB=json` remains a local development mode; its records do not appear in the SQL admin.

Admin soft deletion uses shared `admin_deleted_records` markers. Deleted SQL players cannot
sign in, authenticate with old tokens, appear in player discovery/leaderboards, or enter new
competitions. Their wallets, history, and settlements for already-started matches remain intact.
Restore through the admin's Deleted records view; names remain reserved throughout. Deploy
both the updated game server and admin (`npm run db:migrate`, migration `004_soft_delete.sql`)
against the same SQL database. No APK update is needed for this administration feature.


Open **Community → Watch** (or Watch within any game), select **Demo_A vs Demo_B**,
and look for **DEMO ROOM** and the changing **server signal** number. These clearly
marked mock rooms are streamed over `/social/watch/{id}` every second. They never
charge entry fees or pay rewards. Each competition has a demo so Watch can be tested
with one phone. Live score updates use the same WebSocket channel.

New accounts start with 100 coins. The server charges 10 coins per player when a PvP
room starts, or 5 for an online Bot match. Queueing, cancelling a search, and reconnecting
are free. Winners receive 25 coins in PvP or 12 against an online Bot; draws refund the
entry fee. Duplicate requests and repeated result polling cannot repeat fees or payouts.
Interrupted 2048/Cube rooms are refunded after a server restart. Offline practice has
no server coin rewards. Client-submitted wallet deltas are no longer accepted.

In **Profile → Coin shop**, players request a pack of 50, 200, or 500 coins. These are
manual purchase requests, not a payment checkout. An admin owner verifies payment
externally and approves or rejects the request under **Coin purchases**, recording a
payment reference and review note. Approval credits the shared wallet once and writes
an audit entry. The app refreshes wallet balances automatically. No payment provider
or invented monetary prices are configured.

APK version: **2.0.0 (11)**. Release builds require the existing release signing key;
keep `keystore.properties` and the keystore private so updates retain their signature.

## Visual effects in the client

- Animated page background with gradient, drifting glow orbs, faint grid, and twinkling stars. Reduce effects disables ambient animation and control glows.
- Keyed tile animation: slide tween, merge pop with glow flash, spring spawn, garbage "slam".
- Particle bursts and shock rings on every merge, scaled by tile value and combo.
- Floating score / combo / attack texts, board shake and colour flash on hits.
- Attack meter with gradient fill and pulse when an attack is nearly charged.
- Animated score counters, timer ring with urgency pulse, countdown "3-2-1-GO!".
- Victory confetti, staggered result card, four colour themes (Neon, Sunset, Ocean, Galaxy).
- Procedurally synthesized sound effects (no audio assets) and a small haptic vocabulary.
- A **Reduce effects** switch (settings) turns off ambient animation, particles, shimmer and glow for weak devices.
- Four languages (English, 日本語, 中文, Español) selectable in settings; strings live in `android/src/main/res/values-*/strings.xml`.

## Requirements

- JDK 11 for everything — one JDK builds both the app and the server (AGP 7.3.1 needs 11+;
  Gradle 7.4.2 accepts 11–18, not 21). Server-only builds still work on plain JDK 8.
- Android SDK with platform 33 and build-tools 33 (`local.properties` → `sdk.dir=...`)
- Gradle wrapper is included (Gradle 7.4.2, AGP 7.3.1, Kotlin 1.9.24, Compose BOM 2023.06.00)

The **server** alone still builds with plain JDK 8
(`./gradlew -Pduel2048.serverOnly :server:installDist`) and the built distribution runs on
a JRE 8: `:server` and `:shared` target Java 8 bytecode, and the server dependencies stay on
Java 8 lines (logback 1.3, HikariCP 4).

### Portable JDK (no installer, no admin rights)

`gradlew`, `gradlew-offline` and the `run-server` scripts pick up a project-local JDK
automatically when `JAVA_HOME` is not set: unpack a Temurin JDK zip into the project folder and
leave its `jdk-11.x.x` folder there (or rename it to `jdk`). On Windows:

```
tar -xf OpenJDK11U-jdk_x64_windows_hotspot_11.0.32.1_1.zip     (built-in Windows tar, or use Explorer)
```

An explicit `JAVA_HOME` always wins over the project-local folder.

## Run the server

```bash
./run-server.sh            # Linux/macOS   (PORT=8765 ./run-server.sh for another port)
run-server.bat             # Windows       (set PORT=8765 first for another port)
```

These build the server once and run it as a plain process. You can also use Gradle directly:
`./gradlew :server:run` (Windows: `gradlew.bat :server:run`). With Gradle the console keeps showing
`91% EXECUTING` for as long as the server runs; that is normal, not a hang. Stop with Ctrl+C.

On start the server prints the exact URLs to enter in the app, for example:

```
 2048 Duel server is RUNNING on port 8080.  Press Ctrl+C to stop.
   Android emulator on this PC : ws://10.0.2.2:8080/ws
   Phone on the same Wi-Fi     : ws://192.168.1.23:8080/ws
```

Environment variables: `PORT` (8080), `DB_URL`, `DB_USER`, `DB_PASSWORD` (see the database section),
`DB` (`mysql` or `json`), `BOT_FALLBACK_MS` (8000), `MATCH_DURATION_MS` (150000), `COUNTDOWN_SECONDS` (3),
`BOT_INTERVAL_MS` (550), `BOT_JITTER_MS` (250).

Endpoints: `GET /` (status page), `GET /health`, `GET /stats`, `WS /ws`.

### The app says "Can't reach server"

1. Open `http://localhost:8080/` in a browser on the PC. You should see the status page.
2. Open `http://<PC IP>:8080/` in the **phone's** browser. If that fails the network is the problem, not the app:
   - Allow inbound TCP 8080 for Java in the PC firewall. Windows (admin PowerShell):
     `netsh advfirewall firewall add rule name="2048 Duel" dir=in action=allow protocol=TCP localport=8080`
   - Phone and PC must be on the same Wi-Fi; guest networks with client isolation block this.
   - Use the PC's IPv4 from `ipconfig` / `ip addr`, not 127.0.0.1.
3. In the app settings the URL must start with `ws://` and end with `/ws`.
4. Emulator on the same PC: keep `ws://10.0.2.2:8080/ws` (10.0.2.2 is the emulator's alias for the host).

## Build and install the Android app

```bash
./gradlew :android:assembleDebug
adb install -r android/build/outputs/apk/debug/android-debug.apk
```

In the app, open ⚙ settings and set the server URL:

- Emulator → `ws://10.0.2.2:8080/ws` (default)
- Real device on the same Wi-Fi → `ws://<your-pc-ip>:8080/ws`

Two phones (or a phone and an emulator) pointed at the same server get paired in
**Quick Match**. Alone? The bot joins after 8 seconds, or use **Practice vs Bot**.

## Emulator note

Software-rendered emulators (SwiftShader/ANGLE) can crash the *emulator's* host renderer when the
full effect set is on. If that happens, enable **Reduce effects** in the app settings, use a real
device, or run the emulator with a host GPU. The app itself does not crash; the JVM unit tests and the
server smoke test (`node tools/smoke_client.mjs ws://localhost:8080/ws Smoke BOT`) cover the logic.

For automated tests on release builds the three settings can be forced from adb without touching the UI:

```bash
adb shell settings put global duel2048_lowfx 1
adb shell settings put global duel2048_server ws://10.0.2.2:8080/ws
adb shell settings put global duel2048_lang ja
```

## Landscape client UI (2.0.0)

The Android client now uses the approved landscape design: a floating utility bar, five illustrated game cards, inline mode/difficulty selection, and a first-launch login popup for signed-out users. Registration and guest practice stay on the dashboard. Gameplay, results, queues, watching, invitations, settings, help, support and account views return directly to the dashboard; utilities replace each other rather than building a nested back stack. Existing guest authentication behavior is retained.

All five PvP game fields share a collapsible preset-phrase/emoji chat rail. Offline training, bot matches and spectators cannot send player chat. The cube uses the same Filament renderer and input mapping, with visible face and middle-slice buttons wired to the same move callback. Camera reset changes only the view angle. Game engines, controllers, datasets, protocol, matching, timers, scoring and competition economy are unchanged.

Six generated illustrations ship as small WebP files in `drawable-nodpi`. Mockup screenshots remain outside Git. Motion includes existing ambient/board effects, screen transitions, button feedback and subtle art drift; Reduce Effects disables decorative motion. Core new labels follow the existing English/Spanish/Japanese/Chinese language setting. Dialogs/forms support landscape keyboard insets and internal scrolling, and compact dashboards retain all five game actions.

Match history is an account/server-scoped **device-local presentation history** of completed results observed by this client. It is not a replacement for authoritative server history or online state synchronization. The current server exposes live scores and word grids to spectators; the UI renders those real signals rather than inventing unavailable 2048/cube board snapshots. Purchase requests retain the existing administrator-approval workflow; no sample prices or fake purchase records are shown.

`./gradlew :android:testDebugUnitTest` includes Robolectric/Compose checks for launch login, direct utility return, compact dashboard visibility, native math submission, orthogonal-only letter selection, all cube face/slice button notations, inverse turns and reconnect-disabled input. UI tests may optionally write local review captures when `DUEL_UI_PREVIEW_DIR` points under `/tmp/`; they never save screenshots in the repository. Robolectric verifies the Compose flows; native Filament device rendering is still best checked on Android hardware.

## Math problem rules

Math questions and all four answer choices must be nonnegative whole numbers. Subtraction operands are ordered to avoid negative results, and division is exact. Hard distractors stay within 10 of the correct answer; Very Hard within 3. On both levels, options have the same digit count as the answer and include lower and higher values wherever the digit range allows. Bundled match and training CSVs follow these rules.

The admin's Math problems page can generate a previewed set of 1–100 problems (default 10), balanced across the four operations, and save it as drafts for review and publication. In SQL mode, new math rooms select valid active, nondeleted SQL questions when `math_problems` exists. Publish at least one valid question per level; an empty managed level returns an error rather than serving unpublished content. Without a managed math table, or in JSON development, the server uses its packaged match CSVs. A selected problem is checkpointed, so later edits never change a running round. Apply the admin's migration `005_math_rules_generation.sql` and deploy both updated servers. APK 1.6.1 updates the small offline training catalog.

## Accounts and the MySQL / MariaDB database

Online play needs an account. The app's login screen registers or logs in; the server stores
users and a match history in **MySQL or MariaDB** (tables are created automatically on first start).
Passwords are PBKDF2-SHA256 hashes with per-user salts, never plain text. A login token is
saved on the phone so you stay signed in. Wins, losses, draws, best score and best tile are
updated after every online match, shown on the home screen, and listed on the in-app
**Leaderboard** page (`GET /leaderboard?limit=50`).

### Where the credentials live

Copy **`database.json.example`** to **`database.json`** in the project root (next to `gradlew.bat`)
and edit it. The file is git-ignored and the server reads it on start (from the working directory,
its parent, or the path in `DB_CONFIG`):

```json
{
  "type": "mariadb",
  "host": "127.0.0.1",
  "port": 3306,
  "user": "root",
  "password": "",
  "database": "duel2048",
  "charset": "utf8mb4"
}
```

`type` is `mariadb` (default) or `mysql`; add `"ssl": true` for a TLS-only server. The same
values can be given as `DB_TYPE`, `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`,
`DB_CHARSET`, `DB_SSL` in `server.env` or the environment. For a cloud MySQL
such as Aiven, `DB_URL=mysql://user:password@host:port/db?ssl-mode=REQUIRED` in `server.env`
also works. Other server options (port, bot timing) live in `server.env`, see `server.env.example`.
Never commit `database.json` or `server.env`; if a password has been shared in chat or email, rotate it.

Create the database once (MySQL 8.x or MariaDB 10.6+):

```sql
CREATE DATABASE duel2048 CHARACTER SET utf8mb4;
CREATE USER 'duel2048'@'%' IDENTIFIED BY 'duel2048';
GRANT ALL PRIVILEGES ON duel2048.* TO 'duel2048'@'%';
```

If MySQL is unreachable the server explains what to do and exits. For quick local development
without MySQL set `DB=json`, which keeps users in `data/duel2048-db.json` instead.

Tables: `users` (credentials + stats) and `match_history` (one row per finished match).

## Training mode (offline)

**TRAINING** on the home screen runs a complete duel on the device: two boards, the shared
attack/garbage rules, the timer and a bot (Easy / Normal / Hard) that uses the same `Bot`
class as the server. No server or account is needed, so it is also the quickest way to test
rules, effects and animations. Training results count into the local training record only.

## Release build (signed)

Release builds are always signed. Create the key once:

```bash
tools/create-keystore.sh          # Linux/macOS
tools\create-keystore.bat         # Windows
```

This writes `android/keystore/duel2048-release.jks` and `keystore.properties` (both are
git-ignored; **back them up**, every future update must be signed with the same key). Then:

```bash
./gradlew :android:assembleRelease      # android/build/outputs/apk/release/android-release.apk
./gradlew :android:bundleRelease        # .aab for Google Play
```

Without the key the release build stops with an explanation. The values can also be provided as
environment variables `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (CI).
The server needs no signing; ship it with `./gradlew :server:distZip`.

## Languages

The app follows the system language and can be overridden in settings. To add a language,
copy `android/src/main/res/values/strings.xml` to `values-<tag>/strings.xml`, translate the
values, and add the tag to `Languages.all` in `android/.../ui/Localization.kt`.

## Building without internet

Gradle 7.4.2 does not run on Java 21. On Windows use the `*-jdk11.bat` scripts (they set `JAVA_HOME` to JDK 11 for that process only). Linux and macOS: `./gradlew-offline.sh` with `JAVA_HOME` pointing at JDK 11.

### 1. Fill the offline cache (once, online)

```
gradlew-jdk11.bat downloadDependencies          # Windows
./gradlew downloadDependencies                  # Linux/macOS
```

This builds the client and the server once, then writes two git-ignored folders next to the project:

| Folder | Contents |
|---|---|
| `m2/` | Maven packages: dependencies, Gradle plugins, Kotlin, Android tools, and `aapt2` for Windows, macOS, and Linux |
| `offline/gradle-7.4.2/` | the Gradle distribution |

`git pull` does not create these. Copy the whole project folder, including `m2/` and `offline/`, to a machine that has no network. That machine still needs JDK 11. The Android client also needs the Android SDK (platform 33 and build-tools 33). The server build below does not.

### 2. Build the client offline

```
gradlew-offline-jdk11.bat :android:assembleDebug      # Windows, debug APK
gradlew-offline-jdk11.bat :android:assembleRelease    # Windows, signed release APK
./gradlew-offline.sh :android:assembleDebug           # Linux/macOS
```

Outputs:

- `android\build\outputs\apk\debug\android-debug.apk`
- `android\build\outputs\apk\release\android-release.apk` (needs `tools\create-keystore.bat` once)

### 3. Build the server offline

`-Pduel2048.serverOnly` skips the Android modules, so no Android SDK is required. In PowerShell the property must be quoted, or the dot is split off and Gradle looks for a task named `.serverOnly`.

```
.\gradlew-offline-jdk11.bat "-Pduel2048.serverOnly" :server:installDist    # Windows PowerShell
gradlew-offline-jdk11.bat -Pduel2048.serverOnly :server:installDist        # Windows cmd
./gradlew-offline.sh -Pduel2048.serverOnly :server:installDist             # Linux/macOS
```

Output: `server\build\install\server\` (`bin\server.bat` on Windows, `bin/server` on Linux).

### 4. Run the server offline

Build and start in one step:

```
run-server-offline.bat        # Windows, port 8080
./run-server-offline.sh       # Linux/macOS
```

Or, after step 3, start the distribution directly. It does not use Gradle or the network:

```
server\build\install\server\bin\server.bat
```

`set PORT=8765` before the command changes the port. Accounts need MariaDB or MySQL via `database.json`, or `DB=json` in `server.env` to store them in `data/duel2048-db.json`. The built `server\build\install\server\` folder can be copied to another machine that only has a JRE 8.

### Automatic app update

The installed app checks `GET /app/update` when the home screen opens. If the server's `versionCode` is higher, it downloads the APK and starts the system installer. The new APK must be signed with the **same keystore**. Android shows one confirm screen; a normal app cannot replace itself with no prompt.

On the server machine, next to the process working directory:

```
data/app-release.json       copy of app-release.json.example, with the new versionCode
data/android-release.apk    the release APK from assembleRelease
```

`versionCode` in that JSON must be greater than the one already on the device (`android/build.gradle.kts`). Restart the server after replacing the files.

### Old machine with only JDK 8, no Android SDK

Add `-Pduel2048.serverOnly`: the `:android` module is skipped, so AGP (which requires JDK 11+)
is never resolved and no Android SDK is needed. On the machine with internet, export a
server-only offline bundle:

```
.\gradlew-jdk11.bat "-Pduel2048.serverOnly" downloadDependencies    # Windows PowerShell, online, once
```

Copy the project folder including `m2/` and `offline/` to the old machine, and there:

```
.\gradlew-offline-jdk11.bat "-Pduel2048.serverOnly" :server:installDist    # Windows PowerShell
./gradlew-offline.sh -Pduel2048.serverOnly :server:installDist   # Linux/macOS
run-server-offline.bat                                           # or build + run in one step (serverOnly is built in)
```

A server-only bundle contains no Android/AGP artifacts, so on that machine the flag is required,
not optional. (Alternatively, skip Gradle on the old machine entirely: copy a ready-made
`server/build/install/server/` from any machine — it runs on a JRE 8 with nothing else installed.)

Windows note: if a build stops with "Could not move temporary workspace ... to immutable location",
an antivirus scanner is holding freshly written files in Gradle's transform cache. Delete the
`caches\7.4.2\transforms` folder of the Gradle home it names, add the project and Gradle home
folders to the scanner's exclusions, and re-run. `settings.gradle.kts` also puts `m2/` first in the
repository list whenever it exists, so the normal `gradlew` stops downloading as well. After
changing any version in `gradle/libs.versions.toml`, run `downloadDependencies` again while online.

If `downloadDependencies` reports that a file cannot be written, another program is holding an
exported jar open (Android Studio with the project open, or a Gradle daemon): close it, run
`gradlew --stop`, and re-run. Already exported files are kept.

## Tests

```bash
./gradlew :shared:test        # engine determinism, merge rules, garbage, energy, protocol round-trips
./gradlew :server:test        # JSON store: register/login/token/stats persistence (MySQL is covered by the smoke test)
./gradlew :android:testDebugUnitTest
```

## Protocol

See [docs/PROTOCOL.md](docs/PROTOCOL.md).

## Tuning

All balance constants live in `shared/src/main/kotlin/com/duel2048/shared/engine/Rules.kt`
(attack cost, garbage HP, combo bonus, match length). Because the file is shared, changing
it keeps client prediction and server in sync automatically.

## Credits

See [NOTICE.md](NOTICE.md) for the open-source projects whose ideas or code were reused.
