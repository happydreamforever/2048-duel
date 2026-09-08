// Minimal protocol smoke test: node tools/smoke_client.mjs [ws://host:8080/ws] [name] [BOT|PVP]
// Plays random moves until the match ends and prints a summary. Node 22+ (built-in WebSocket).
const url = process.argv[2] || 'ws://localhost:8080/ws';
const name = process.argv[3] || 'Smoke';
const mode = process.argv[4] || 'BOT';
const dirs = ['UP', 'DOWN', 'LEFT', 'RIGHT'];
const account = { name: `${name}_${Math.floor(Math.random() * 9000 + 1000)}`, password: 'smoke1234' };

const ws = new WebSocket(url);
let matchId = null, myId = null, seq = 0, moves = 0, acks = 0, garbageIn = 0, garbageOut = 0, oppMoves = 0, ticks = 0;
let last = null, timer = null;
const send = (o) => ws.send(JSON.stringify(o));
const summary = () => console.log(JSON.stringify({ name, moves, acks, oppMoves, garbageIn, garbageOut, ticks, score: last?.score, gameOver: last?.gameOver }));

ws.onopen = () => console.log(`[${name}] connected to ${url}`);
ws.onmessage = (ev) => {
  const m = JSON.parse(ev.data);
  switch (m.type) {
    case 'welcome': myId = m.playerId; send({ type: 'hello', name, clientVersion: m.serverVersion }); send({ type: 'register', ...account }); break;
    case 'auth_ok': console.log(`[${name}] logged in as ${m.name} token=${m.token.slice(0, 6)}… stats=${JSON.stringify(m.stats)}`); send({ type: 'find_match', mode }); break;
    case 'auth_failed': if (m.code === 'name_taken') { send({ type: 'login', ...account }); } else { console.log(`[${name}] auth failed`, m); process.exit(1); } break;
    case 'stats': console.log(`[${name}] stats updated ${JSON.stringify(m.stats)}`); break;
    case 'queued': console.log(`[${name}] queued (bot fallback ${m.botFallbackMs} ms)`); break;
    case 'match_found': matchId = m.matchId; last = m.yourState; console.log(`[${name}] match ${matchId} vs ${m.opponent.name} (bot=${m.opponent.isBot}) seed=${m.seed}`); break;
    case 'countdown': console.log(`[${name}] countdown ${m.secondsLeft}`); break;
    case 'match_started':
      console.log(`[${name}] started, ${m.remainingMs} ms`);
      timer = setInterval(() => {
        if (!matchId || last?.gameOver) return;
        send({ type: 'move', matchId, seq: seq++, direction: dirs[Math.floor(Math.random() * 4)] });
        moves++;
      }, 140);
      break;
    case 'move_ack': acks++; last = m.state; garbageOut += m.events.garbageSent || 0; break;
    case 'move_rejected': console.log(`[${name}] rejected seq=${m.seq} reason=${m.reason} expected=${m.expectedSeq}`); seq = m.expectedSeq; last = m.state; break;
    case 'opponent_moved': oppMoves++; break;
    case 'garbage': if (m.targetId === myId) garbageIn += m.placements.length; break;
    case 'tick': ticks++; break;
    case 'match_over': console.log(`[${name}] over: reason=${m.reason} winner=${m.winnerId} me=${myId} results=${JSON.stringify(m.results)}`); summary(); clearInterval(timer); ws.close(); break;
    case 'error': console.log(`[${name}] error`, m); break;
    case 'pong': break;
  }
};
ws.onclose = () => { console.log(`[${name}] closed`); process.exit(0); };
ws.onerror = (e) => { console.log(`[${name}] ws error`, e.message || e); process.exit(1); };
setTimeout(() => { console.log(`[${name}] timeout`); summary(); process.exit(2); }, 240_000);
