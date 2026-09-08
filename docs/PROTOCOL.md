# Wire protocol

JSON over a single WebSocket (`/ws`). Every message has a `type` discriminator.
Types are defined once in `shared/.../protocol/Messages.kt` and serialized with
kotlinx.serialization on both ends.

## Client → server

| type | fields | notes |
|---|---|---|
| `hello` | `name`, `clientVersion` | sent after `welcome` |
| `register` | `name`, `password` | creates an account, answers `auth_ok` or `auth_failed` |
| `login` | `name`, `password` | answers `auth_ok` (new token) or `auth_failed` |
| `auth` | `token` | resume with a saved token; required before `find_match` |
| `logout` | – | invalidates the token |
| `find_match` | `mode`: `PVP` \| `BOT` | `PVP` queues; a bot fills in after `BOT_FALLBACK_MS` |
| `cancel_find` | – | leave the queue |
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
