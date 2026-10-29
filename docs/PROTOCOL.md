# Wire protocol

JSON over a single WebSocket (`/ws`). Every message has a `type` discriminator.
Types are defined once in `shared/.../protocol/Messages.kt` and serialized with
kotlinx.serialization on both ends.

Protocol version 7 adds server-selected puzzle questions, multiple-choice math answers,
and word answers assembled from letter tile indices. It retains account saves and
authoritative match reconnection, outer face turns, and the three middle slices
(`M`, `E`, `S`), including inverse (`'`) and double (`2`) variants. A solved cube
has uniform faces in any orientation, since middle slices move the centers.
Rebuild and update both the server and Android app together; older versions
cannot represent middle-slice moves in cube races.

## Client → server

| type | fields | notes |
|---|---|---|
| `hello` | `name`, `clientVersion` | sent after `welcome` |
| `register` | `name`, `password` | creates an account, answers `auth_ok` or `auth_failed` |
| `login` | `name`, `password` | answers `auth_ok` or `auth_failed`; signing in on a second device preserves the current account token |
| `auth` | `token` | resume with a saved token; required before `find_match` |
| `logout` | – | invalidates the token |
| `find_match` | `mode`: `PVP` \| `BOT` | `PVP` queues; a bot fills in after `BOT_FALLBACK_MS` |
| `cancel_find` | – | leave the queue |
| `resume_match` | `matchId` | after authentication, reclaim an active seat owned by the same account; disconnect grace is 90 seconds |
| `request_match_state` | `matchId` | request a full authoritative snapshot of the attached match |
| `move` | `matchId`, `seq`, `direction` | `seq` starts at 0 and increments per move |
| `leave` | `matchId` | forfeit |
| `ping` | `clientTime` | answered with `pong` |

## Server → client

| type | fields | notes |
|---|---|---|
| `welcome` | `playerId`, `serverVersion`, `onlinePlayers`, `registeredUsers` | |
| `auth_ok` | `playerId`, `name`, `token`, `stats` | store `token` for `auth` next time |
| `auth_failed` | `code`, `message` | `name_taken`, `invalid_name`, `invalid_password`, `bad_credentials`, `bad_token` |
| `stats` | `stats` | fresh account stats after every finished match |
| `queued` | `position`, `botFallbackMs` | |
| `match_found` | `matchId`, `you`, `opponent`, `seed`, `boardSize`, `durationMs`, `countdownSeconds`, `yourState`, `opponentState` | both states are generated from the same `seed` |
| `countdown` | `matchId`, `secondsLeft` | 3, 2, 1 |
| `match_started` | `matchId`, `remainingMs` | moves are accepted from now on |
| `match_restored` | `game` | full `SavedGame` checkpoint, stable player identity, next `expectedSeq`, timer, and optional result; discard predictions and restore from it |
| `tick` | `matchId`, `remainingMs` | once per second |
| `move_ack` | `matchId`, `seq`, `state`, `events` | authoritative result of your move |
| `move_rejected` | `matchId`, `seq`, `expectedSeq`, `reason`, `state` | resync to `state`, continue from `expectedSeq` |
| `opponent_moved` | `matchId`, `state`, `events` | drives the opponent's live board |
| `garbage` | `matchId`, `targetId`, `fromId`, `state`, `placements` | garbage landed on `targetId`'s board |
| `match_over` | `matchId`, `winnerId?`, `reason`, `results[]` | `reason`: `BOARD_FULL`, `TIME_UP`, `FORFEIT` |
| `error` | `code`, `message` | |
| `pong` | `clientTime`, `serverTime` | |

## Flow

```
client                                   server
  │── connect ────────────────────────────►│
  │◄── welcome ────────────────────────────│
  │── hello, auth(token) ─────────────────►│  (or register / login)
  │◄── auth_ok(stats) ─────────────────────│
  │── find_match ─────────────────────────►│  queue / pair / bot fallback
  │◄── queued ─────────────────────────────│
  │◄── match_found (seed, both states) ────│
  │◄── countdown 3,2,1 · match_started ────│
  │── move(seq=0) ────────────────────────►│  engine.move on server copy
  │   (client already animated its         │
  │    prediction with the same engine)    │
  │◄── move_ack(seq=0, state, events) ─────│  client compares syncKey, snaps if different
  │◄── opponent_moved ─────────────────────│
  │◄── garbage(target=me) ─────────────────│  when the opponent's attack meter fires
  │◄── tick ... ───────────────────────────│
  │◄── match_over ─────────────────────────│
  │◄── stats ──────────────────────────────│  persisted in MySQL (users, match_history)
```

## Game state

`GameState` is fully serializable (board cells with tile ids, score, RNG state,
attack energy, counters) so any message that carries it lets the client resync
without extra round-trips. `GameState.syncKey()` is the comparison used for prediction.

## HTTP

| Endpoint | Returns |
|---|---|
| `GET /` | HTML status page |
| `GET /health` | `ok` |
| `GET /stats` | `{onlinePlayers, queued, activeMatches, totalMatches}` |
| `GET /leaderboard?limit=50` | `[{name, wins, losses, draws, matches, bestScore, bestTile}]`, sorted by wins then best score |
| `GET /games/saves` | `SavedGameList` for the Bearer-authenticated account, including deletion tombstones |
| `POST /games/saves` | versioned `SavedGame`; Bearer authentication required; returns the accepted checkpoint |

### Save synchronization

`Authorization: Bearer <account token>` is required for both save endpoints. Each save has
an immutable `id`, `game` (`TILES`, `CUBE`, `MATH`, `WORD`, `WORD_PUZZLE`), and `mode` (`SOLO`, `TRAINING`,
`BOT`, `ONLINE`). Send revision `0` to create a save; later updates must send the last
accepted revision. The server increments it and sets `updatedAt`. A stale revision returns
HTTP **409** with the current checkpoint so the client can ask which version to retain.
Unauthorized requests return **401**; invalid states and client-authored `ONLINE` saves return
**400**. Clients delete by submitting `deleted=true` with the current revision. Tombstones
prevent another device from resurrecting a deleted game.

Cube saves contain `seed`, `played`, and `opponentPlayed`. Reconstruct the scramble from
the seed, then apply the recorded moves in order. Tile saves contain `board` and, for duels,
`opponentBoard`; both carry full engine state. Puzzle saves include `prompt`, `grid`,
`solution` (local puzzles only), and an optional server `roomId`. Timed saves contain
`remainingMs` and `durationMs`; difficulty and guided-play preferences are preserved.

Only the server writes `ONLINE` snapshots, synchronously before acknowledging accepted
moves. `resumable=false` on a listed unfinished online snapshot means its live match no
longer exists. Snapshots survive a server restart, but live cube/2048 seats currently do
not. During the 90-second disconnect grace, the match timer and opponent keep running.
After reconnection, authenticate then send `resume_match` rather than `find_match`.
The server sends `match_restored` with the next sequence number. Reclaiming a live seat
closes its old socket with code **4001** (`session_replaced`); that device must stop retrying.

Math/word room creation and answers require `token` in the request body. The server uses
the authenticated account name, verifies room membership on answers, and persists room
content, participants, results, and the absolute start timestamp in `social.json`.

### Dataset puzzle matches

`POST /social/mini/join` accepts `game` (`math` or `word`), `mode` (`pvp` or `bot`),
`difficulty`, and `token`. Math difficulties are `easy`, `medium`, `hard`, `very_hard`;
word difficulties are lengths `4`, `5`, `6`, `7`, `8`. Unknown levels return 400. Training
is local and uses only the small APK catalog; the join endpoint does not accept `train`.

`MiniRoomView` contains the chosen `prompt`, `grid` (shuffled word letters), `choices`
(four shuffled math answers), `puzzleId`, and per-player `answered` flag. The solution
is empty until `status=over`. Word puzzle IDs never encode their answer. Both competitors
receive the same problem and choice order, which are checkpointed unchanged.

`POST /social/mini/answer` accepts `{roomId,token,name,answer,selectedLetters}`. The server
ignores the supplied player name and authenticates the token. Math answers must match a
displayed choice. Word answers are reconstructed from `selectedLetters` (zero-based indices
into concatenated `grid` strings); duplicate or out-of-range indices, and incomplete words,
are not accepted as submissions. The supplied `answer` text is ignored for word puzzles.
An incorrect valid submission locks that player out; both incorrect submissions end a PvP
match without a winner. Bot matches are server-owned and the bot solves halfway through
the time limit. Polling restores the question, choices, timer, and answered state after reconnect.

`SavedGame` additionally persists `choices`, `puzzleId`, `puzzleMode`, and `selectedLetters`.
Clients never need the full match catalog. The server sends individual questions only.


### Competition updates (Android 1.4.0)

The product no longer browses or resumes local saves. Online 2048/Cube reconnects use
authoritative server checkpoints automatically. `WORD` / `word` identifies Word Quiz;
`WORD_PUZZLE` / `word_puzzle` identifies the orthogonal grid competition. Word dictionaries
now cover lengths 3–8. Clients submit cell indices; the server checks adjacency, dictionary
membership, and per-player duplicate words. Diagonals and reused cells are forbidden.

`GET /social/watch/{id}` upgrades to WebSocket and pushes `WatchFrame` once per second:
`room`, `sequence`, `serverTime`, `demo`, `grid`, `selected`, `event`, `seconds`. IDs starting
with `demo-` are clearly marked simulations and have no wallet entries or payouts.

`GET /social/wallet` accepts Bearer authentication. `POST /social/wallet` is retired (410);
clients cannot supply coin or score deltas. The server debits room entry on match start
and settles winners/draws idempotently. `POST /social/coins/purchases` accepts `token`,
`id` (client-generated request UUID), and `coins` (50, 200, or 500), returning a pending
`CoinPurchase`. Only administrative payment approval credits its balance.
