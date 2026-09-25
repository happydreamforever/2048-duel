# 2048 Duel

Real-time 1v1 **2048 PvP** for Android with an authoritative Kotlin server.

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
| `android/` | Jetpack Compose client: login, home, leaderboard, matchmaking, duel, result, offline **training** (full duel rules vs an on-device bot) and solo screens. |

## Visual effects in the client

- Living background: gradient, faint grid, drifting glow orbs and twinkling stars.
- Keyed tile animation: slide tween, merge pop with glow flash, spring spawn, garbage "slam".
- Particle bursts and shock rings on every merge, scaled by tile value and combo.
- Floating score / combo / attack texts, board shake and colour flash on hits.
- Attack meter with gradient fill and pulse when an attack is nearly charged.
- Animated score counters, timer ring with urgency pulse, countdown "3-2-1-GO!".
- Victory confetti, staggered result card, four colour themes (Neon, Sunset, Ocean, Galaxy).
- Procedurally synthesized sound effects (no audio assets) and a small haptic vocabulary.
- A **Reduce effects** switch (settings) turns off ambient animation, particles, shimmer and glow for weak devices.
- Four languages (English, 日本語, 中文, Español) selectable in settings; strings live in `android/src/main/res/values-*/strings.xml`.

## Screenshots

Captured on an API 35 emulator (software renderer, so **Reduce effects** was on: no particles or glow here).

| Home (logged in) | Login | Duel | Result | Solo |
|---|---|---|---|---|
| ![home](docs/screenshots/home.png) | ![login](docs/screenshots/login.png) | ![duel](docs/screenshots/duel.png) | ![result](docs/screenshots/result.png) | ![solo](docs/screenshots/solo.png) |

| Training chooser | Training duel (offline) | Countdown | Leaderboard |
|---|---|---|---|
| ![training sheet](docs/screenshots/training-sheet.png) | ![training](docs/screenshots/training.png) | ![countdown](docs/screenshots/countdown.png) | ![leaderboard](docs/screenshots/leaderboard.png) |

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
