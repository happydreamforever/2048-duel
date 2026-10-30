package com.duel2048.server.db

import com.duel2048.shared.protocol.Protocol
import com.duel2048.shared.social.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.sql.Connection
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Shared SQL ledger in production, atomic file ledger for DB=json development. */
class CompetitionStore(private val users: UserStore, dataDir: String) {
    private val file = File(dataDir, "competition-ledger.json")
    private var data = if (users !is MySqlStore && file.isFile) Protocol.json.decodeFromString(Ledger.serializer(), file.readText()) else Ledger()
    init {
        if (users is MySqlStore) users.connection { c ->
            val ddl = checkNotNull(javaClass.getResourceAsStream("/db/competitions.sql")).bufferedReader().use { it.readText() }
            ddl.split(';').filter { it.isNotBlank() }.forEach { sql -> c.createStatement().use { it.execute(sql) } }
        }
        val legacy=File(dataDir,"social.json")
        if(legacy.isFile) {
            val wallets=Protocol.json.parseToJsonElement(legacy.readText()).jsonObject["wallets"]?.jsonObject.orEmpty()
            if(wallets.isNotEmpty()) tx { c -> wallets.forEach { (id,value) ->
                val old=Protocol.json.decodeFromJsonElement(WalletView.serializer(),value)
                if(c==null) {
                    if(id !in data.wallets) data=data.copy(wallets=data.wallets+(id to old))
                } else {
                    val exists=c.prepareStatement("SELECT id FROM users WHERE id=?").use { ps -> ps.setString(1,id);ps.executeQuery().use { it.next() } }
                    if(exists) execute(c,"INSERT IGNORE INTO wallets(user_id,score,coins,updated_at) VALUES (?,?,?,?)",id,old.score.coerceAtLeast(0),old.coins.coerceAtLeast(0),System.currentTimeMillis())
                }
            } }
        }
    }
    @Synchronized private fun <T> tx(block: (Connection?) -> T): T {
        if (users is MySqlStore) return users.connection { c ->
            c.autoCommit = false
            try { val result = block(c); c.commit(); result }
            catch (e: Exception) { c.rollback(); throw e }
            finally { c.autoCommit = true }
        }
        val before = data
        try {
            val result = block(null)
            file.parentFile.mkdirs()
            val tmp = Files.createTempFile(file.parentFile.toPath(), "ledger-", ".tmp")
            try {
                java.io.FileOutputStream(tmp.toFile()).use { out -> out.write(Protocol.json.encodeToString(Ledger.serializer(), data).toByteArray()); out.fd.sync() }
                try { Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
                catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            } finally { Files.deleteIfExists(tmp) }
            return result
        } catch (e: Exception) { data = before; throw e }
    }
    private fun execute(c: Connection, sql: String, vararg args: Any?): Int = c.prepareStatement(sql).use { ps ->
        args.forEachIndexed { i, v -> ps.setObject(i + 1, v) }; ps.executeUpdate()
    }
    private fun wallet(c: Connection?, id: String): WalletView {
        if (c == null) return data.wallets[id] ?: WalletView()
        execute(c, "INSERT IGNORE INTO wallets(user_id,score,coins,updated_at) VALUES (?,0,100,?)", id, System.currentTimeMillis())
        return c.prepareStatement("SELECT score,coins FROM wallets WHERE user_id=? FOR UPDATE").use { ps ->
            ps.setString(1,id); ps.executeQuery().use { rs -> check(rs.next()); WalletView(rs.getInt(1), rs.getInt(2)) }
        }
    }
    fun wallet(id: String): WalletView = tx { wallet(it,id) }
    private fun change(c: Connection?, id: String, coins: Int, score: Int, key: String, reason: String) {
        val old = wallet(c,id)
        val duplicate = if (c == null) key in data.operations else
            execute(c,"INSERT IGNORE INTO competition_operations(operation_key,created_at) VALUES (?,?)",key,System.currentTimeMillis()) == 0
        if (duplicate) return
        require(old.coins.toLong()+coins in 0..Int.MAX_VALUE.toLong() && old.score.toLong()+score in 0..Int.MAX_VALUE.toLong()) { "Insufficient coins" }
        val next = WalletView(old.score+score,old.coins+coins)
        if (c == null) data = data.copy(wallets=data.wallets+(id to next),operations=data.operations+key)
        else {
            execute(c,"UPDATE wallets SET score=?,coins=?,revision=revision+1,updated_at=? WHERE user_id=?",next.score,next.coins,System.currentTimeMillis(),id)
            execute(c,"INSERT INTO wallet_transactions(user_id,score_delta,coin_delta,reason,actor_id,created_at) VALUES (?,?,?,?,NULL,?)",id,score,coins,reason,System.currentTimeMillis())
        }
    }
    fun enter(room: String, game: String, mode: String, ids: List<String>) = tx { c ->
        // Lock accounts before their wallets, matching the admin deletion lock order.
        // The eligibility check and debit commit together for every competition type.
        if (c != null) ids.distinct().sorted().forEach { id ->
            val exists = c.prepareStatement("SELECT id FROM users WHERE id=? FOR UPDATE").use { ps ->
                ps.setString(1,id); ps.executeQuery().use { it.next() }
            }
            require(exists) { "A player account is unavailable" }
            val deleted = c.prepareStatement("SELECT deleted_at FROM admin_deleted_records WHERE entity_type='players' AND record_id=? FOR UPDATE").use { ps ->
                ps.setString(1,id); ps.executeQuery().use { it.next() && it.getObject(1) != null }
            }
            require(!deleted) { "A player account is unavailable" }
        }
        ids.distinct().sorted().forEach { id ->
            change(c,id,-Economy.joinCost(mode),0,"entry:$room:$id","$game $mode room entry $room")
            if (c == null) data = data.copy(entries=data.entries+("$room:$id" to Entry(room,id,game,mode,Economy.joinCost(mode))))
            else execute(c,"INSERT IGNORE INTO competition_entries(room_id,user_id,game,mode,fee,created_at) VALUES (?,?,?,?,?,?)",room,id,game,mode,Economy.joinCost(mode),System.currentTimeMillis())
        }
    }
    private fun entries(c:Connection?,room:String):List<Entry> {
        return if (c == null) data.entries.values.filter { it.room == room } else
            c.prepareStatement("SELECT user_id,game,mode,fee FROM competition_entries WHERE room_id=? ORDER BY user_id FOR UPDATE").use { ps ->
                ps.setString(1,room); ps.executeQuery().use { rs -> buildList { while(rs.next()) add(Entry(room,rs.getString(1),rs.getString(2),rs.getString(3),rs.getInt(4))) } }
            }
    }
    fun recoverInterruptedMatches() {
        rooms().filter { it.game in listOf("2048","cube") && it.status=="playing" && it.mode!="demo" }.forEach { room ->
            tx { c -> entries(c,room.id).forEach { entry -> change(c,entry.user,entry.fee,0,"interrupted:${room.id}:${entry.user}","Interrupted match refund ${room.id}") } }
            checkpoint(room.id,room.game,room.mode,"interrupted",room.host,room.opponent,room.scoreA,room.scoreB,null,room.payload)
        }
    }
    fun settle(room: String, winner: String?, draw: Boolean) = tx { c ->
        val entries = entries(c,room)
        entries.forEach { entry ->
            val won = entry.user == winner
            val payout = Economy.payout(entry.mode,won)
            change(c,entry.user,if(draw) entry.fee else payout.first, if(draw) 5 else payout.second,"settle:$room:${entry.user}",if(draw) "Draw refund $room" else "Match result $room")
            if (c != null) execute(c,"UPDATE competition_entries SET status='settled' WHERE room_id=? AND user_id=?",room,entry.user)
        }
    }
    fun checkpoint(id: String, game: String, mode: String, status: String, host: String, opponent: String, a: Int, b: Int, winner: String?, payload: String) = tx { c ->
        val existing = if(c==null) data.rooms[id]?.status else c.prepareStatement("SELECT status FROM competition_rooms WHERE id=? FOR UPDATE").use { ps -> ps.setString(1,id);ps.executeQuery().use { rs -> if(rs.next()) rs.getString(1) else null } }
        if(existing=="over" && status!="over") return@tx
        val room = RoomCheckpoint(id,game,mode,status,host,opponent,a,b,winner,payload,System.currentTimeMillis())
        if(c == null) data=data.copy(rooms=data.rooms+(id to room)) else execute(c,
            "INSERT INTO competition_rooms(id,game,mode,status,host,opponent,score_a,score_b,winner_name,payload,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status),opponent=VALUES(opponent),score_a=VALUES(score_a),score_b=VALUES(score_b),winner_name=VALUES(winner_name),payload=VALUES(payload),updated_at=VALUES(updated_at)",
            id,game,mode,status,host,opponent,a,b,winner,payload,room.updatedAt)
    }
    fun rooms(): List<RoomCheckpoint> = tx { c ->
        if(c==null) data.rooms.values.toList() else c.prepareStatement("SELECT * FROM competition_rooms").use { ps -> ps.executeQuery().use { rs -> buildList {
            while(rs.next()) add(RoomCheckpoint(rs.getString("id"),rs.getString("game"),rs.getString("mode"),rs.getString("status"),rs.getString("host"),rs.getString("opponent"),rs.getInt("score_a"),rs.getInt("score_b"),rs.getString("winner_name"),rs.getString("payload"),rs.getLong("updated_at")))
        } } }
    }
    fun purchase(user: String, id: String, coins: Int): CoinPurchase = tx { c ->
        require(id.matches(Regex("[A-Za-z0-9-]{1,100}")) && coins in listOf(50,200,500)) { "Invalid purchase" }
        val existing = purchases(c,user).firstOrNull { it.id==id }
        if(existing!=null) { require(existing.coins==coins); return@tx existing }
        require(purchases(c,user).count { it.status=="pending" }<5) { "Please wait for pending purchases to be reviewed" }
        val order=CoinPurchase(id,coins,"pending",System.currentTimeMillis())
        if(c==null) data=data.copy(purchases=data.purchases+("$user:$id" to order)) else execute(c,
            "INSERT INTO coin_purchase_orders(id,user_id,coins,status,created_at,updated_at) VALUES (?,?,?,'pending',?,?)",id,user,coins,order.createdAt,order.createdAt)
        order
    }
    private fun purchases(c: Connection?,user:String):List<CoinPurchase> = if(c==null) data.purchases.filterKeys { it.startsWith("$user:") }.values.toList() else
        c.prepareStatement("SELECT id,coins,status,created_at FROM coin_purchase_orders WHERE user_id=? ORDER BY created_at DESC LIMIT 50 FOR UPDATE").use { ps -> ps.setString(1,user);ps.executeQuery().use { rs -> buildList { while(rs.next()) add(CoinPurchase(rs.getString(1),rs.getInt(2),rs.getString(3),rs.getLong(4))) } } }
    fun purchases(user:String):List<CoinPurchase> = tx { purchases(it,user) }
}
@Serializable private data class Entry(val room:String,val user:String,val game:String,val mode:String,val fee:Int)
@Serializable data class RoomCheckpoint(val id:String,val game:String,val mode:String,val status:String,val host:String,val opponent:String,val scoreA:Int,val scoreB:Int,val winner:String?,val payload:String,val updatedAt:Long)
@Serializable private data class Ledger(val wallets:Map<String,WalletView> = emptyMap(),val operations:Set<String> = emptySet(),val entries:Map<String,Entry> = emptyMap(),val rooms:Map<String,RoomCheckpoint> = emptyMap(),val purchases:Map<String,CoinPurchase> = emptyMap())
