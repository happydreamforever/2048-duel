package com.duel2048.server

import com.duel2048.server.db.*
import com.duel2048.shared.protocol.*
import com.duel2048.shared.social.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Authenticated invitations. Public queues and Bot fallback never consume these seats. */
class ChallengeService(
    private val db: UserStore,
    private val lobby: Lobby,
    private val social: SocialService,
    dataDir: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()
    private val file = File(dataDir,"pvp-challenges.json")
    private val records = linkedMapOf<String,Challenge>()
    private val seats = mutableMapOf<String,MutableMap<String,PlayerSession>>()
    init {
        val loaded = if(db is MySqlStore) db.connection { c ->
            c.prepareStatement("SELECT payload FROM pvp_challenges WHERE status IN ('pending','accepted','playing') OR updated_at>? ORDER BY updated_at DESC LIMIT 5000").use { ps ->
                ps.setLong(1,now()-86_400_000);ps.executeQuery().use { rs -> buildList {
                    while(rs.next()) add(Protocol.json.decodeFromString(Challenge.serializer(),rs.getString(1)))
                } }
            }
        } else if(file.isFile) Protocol.json.decodeFromString(ListSerializer(Challenge.serializer()),file.readText()) else emptyList()
        loaded.forEach { original ->
            val recovered = if(original.game in listOf("2048","cube") && original.status in listOf("accepted","playing")) original.copy(status="expired",ready=emptyList())
                else original.copy(ready=emptyList())
            records[recovered.id]=recovered
        }
        loaded.filter { records[it.id]!=it }.forEach { persist(records.getValue(it.id)) }
        lobby.challenges=this
    }
    private fun persist(value:Challenge) {
        if(db is MySqlStore) db.connection { c ->
            c.prepareStatement("INSERT INTO pvp_challenges(id,from_user_id,to_user_id,status,payload,expires_at,updated_at) VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status),payload=VALUES(payload),updated_at=VALUES(updated_at)").use { ps ->
                val args=listOf(value.id,value.fromId,value.toId,value.status,Protocol.json.encodeToString(Challenge.serializer(),value),value.expiresAt,now())
                args.forEachIndexed { i,v -> ps.setObject(i+1,v) };ps.executeUpdate()
            }
        } else {
            file.parentFile.mkdirs()
            val tmp=Files.createTempFile(file.parentFile.toPath(),"challenges-",".tmp")
            try {
                java.io.FileOutputStream(tmp.toFile()).use { out -> out.write(Protocol.json.encodeToString(ListSerializer(Challenge.serializer()),records.values.toList()).toByteArray());out.fd.sync() }
                try { Files.move(tmp,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
                catch(_:java.nio.file.AtomicMoveNotSupportedException) { Files.move(tmp,file.toPath(),StandardCopyOption.REPLACE_EXISTING) }
            } finally { Files.deleteIfExists(tmp) }
        }
    }
    private fun update(value:Challenge):Challenge {
        val before=records.put(value.id,value)
        try { persist(value) } catch(e:Exception) { if(before==null) records.remove(value.id) else records[value.id]=before;throw e }
        return value
    }
    private fun roomId(c:Challenge):String = c.reservedRoomId
    private fun view(c:Challenge,userId:String)=ChallengeView(c.id,c.fromName,c.toName,c.game,c.difficulty,c.status,c.expiresAt,userId==c.toId,
        userId in c.ready,(if(userId==c.fromId)c.toId else c.fromId) in c.ready,c.roomId)
    private fun own(id:String,userId:String):Challenge = records[id]?.takeIf { userId==it.fromId || userId==it.toId }
        ?: throw IllegalArgumentException("Invitation not found")
    private fun expire() {
        records.values.toList().forEach { c ->
            if(c.status=="accepted" && c.game !in listOf("2048","cube")) {
                val existing=social.privateRoom(roomId(c),c.id)
                if(existing!=null) { update(c.copy(status=if(existing.status in listOf("waiting","playing")) "playing" else "completed",roomId=existing.roomId));return@forEach }
            }
            if(c.status in listOf("pending","accepted") && c.expiresAt<=now()) {
                update(c.copy(status="expired",ready=emptyList()))
                seats.remove(c.id)?.values?.forEach { it.privateChallengeId=null;it.send(ErrorMsg("challenge_error","Invitation expired")) }
            } else if(c.status=="playing") {
                val active=if(c.game in listOf("2048","cube")) lobby.isRoomActive(c.roomId) else social.mini(c.roomId,c.fromName)?.status in listOf("waiting","playing")
                if(!active) update(c.copy(status="completed",ready=emptyList()))
            }
        }
    }
    private fun available(id:String)=!lobby.userBusy(id) && !social.userBusy(id)
    private fun checkFunds(c:Challenge) {
        require(lobby.economy.wallet(c.fromId).coins>=10 && lobby.economy.wallet(c.toId).coins>=10) { "Both players need 10 coins to enter" }
    }
    suspend fun list(userId:String):List<ChallengeView> = lock.withLock {
        expire()
        records.values.filter { it.fromId==userId || it.toId==userId }.sortedByDescending { it.createdAt }.take(50).map { view(it,userId) }
    }
    suspend fun create(user:UserRecord,body:ChallengeCreateBody):ChallengeView {
        val target=db.userByName(body.opponent) ?: throw IllegalArgumentException("Player not found. Enter their exact account name")
        require(target.id!=user.id) { "Choose another player" }
        require(body.requestId.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))) { "Invalid invitation ID" }
        require(body.game in listOf("2048","cube","math","word","word_puzzle")) { "Unknown game" }
        require(when(body.game) { "math" -> body.difficulty in Puzzles.mathLevels; "word" -> body.difficulty.toIntOrNull() in Puzzles.wordLengths; "word_puzzle" -> body.difficulty.toIntOrNull() in WordGrid.sizes;else -> body.difficulty in listOf("easy","normal","hard") }) { "Invalid difficulty" }
        return lock.withLock {
            expire()
            records[body.requestId]?.let { c ->
                require(c.fromId==user.id && c.toId==target.id && c.game==body.game && c.difficulty==body.difficulty) { "Invitation ID already used" }
                return@withLock view(c,user.id)
            }
            require(available(user.id)) { "Finish or leave your current competition first" }
            records.values.firstOrNull { it.fromId==user.id && it.toId==target.id && it.game==body.game && it.difficulty==body.difficulty && it.status in listOf("pending","accepted") }?.let { return@withLock view(it,user.id) }
            require(records.values.count { it.fromId==user.id && it.status in listOf("pending","accepted") }<5) { "Wait for your existing invitations or cancel one" }
            val reserved=(if(body.game=="cube") "c" else if(body.game=="2048") "m" else "p")+java.util.UUID.randomUUID().toString().replace("-","").take(15)
            val c=Challenge(body.requestId,user.id,target.id,user.name,target.name,body.game,body.difficulty,"pending",now(),now()+120_000,reservedRoomId=reserved)
            view(update(c),user.id)
        }
    }
    suspend fun action(user:UserRecord,id:String,action:String):ChallengeView = lock.withLock {
        expire()
        var c=own(id,user.id)
        when(action) {
            "accept" -> {
                require(user.id==c.toId) { "Only the invited player can accept" }
                if(c.status=="accepted" || c.status=="playing") return@withLock view(c,user.id)
                require(c.status=="pending") { "This invitation is no longer pending" }
                require(available(c.fromId) && available(c.toId)) { "A player is already in another competition" }
                checkFunds(c)
                c=update(c.copy(status="accepted"))
            }
            "decline" -> { require(user.id==c.toId && c.status=="pending") { "This invitation cannot be declined" };c=update(c.copy(status="declined")) }
            "cancel" -> {
                if(c.status=="cancelled") return@withLock view(c,user.id)
                require(c.status in listOf("pending","accepted") && (user.id==c.fromId || c.status=="accepted")) { "This invitation cannot be cancelled" }
                c=update(c.copy(status="cancelled",ready=emptyList()))
                seats.remove(c.id)?.values?.forEach { it.privateChallengeId=null;it.send(ErrorMsg("private_cancelled","The invitation was cancelled")) }
            }
            "ready" -> {
                require(c.game !in listOf("2048","cube")) { "Connect to the private competition to join" }
                if(c.status=="playing") return@withLock view(c,user.id)
                require(c.status=="accepted") { "Accept this invitation before joining" }
                require(available(c.fromId) && available(c.toId)) { "A player is already in another competition" }
                checkFunds(c)
                c=c.copy(ready=(c.ready+user.id).distinct())
                if(c.ready.size==2) {
                    val room=social.createPrivate(roomId(c),c.game,c.difficulty,c.fromName,c.fromId,c.toName,c.toId,c.id)
                    c=c.copy(status="playing",roomId=room.roomId)
                }
                c=update(c)
            }
            else -> throw IllegalArgumentException("Unknown invitation action")
        }
        view(c,user.id)
    }
    suspend fun join(session:PlayerSession,id:String,game:GameType) = lock.withLock {
        expire()
        val userId=session.userId ?: throw IllegalArgumentException("Sign in to join")
        var c=own(id,userId)
        require(c.game==if(game==GameType.CUBE_SOLVE) "cube" else "2048") { "Wrong competition type" }
        if(c.status=="playing") { lobby.resumePrivate(session,c.roomId);return@withLock }
        require(c.status=="accepted") { "This invitation is not ready or has expired" }
        require(!social.userBusy(userId) && !lobby.userBusy(userId,c.id)) { "Finish your current competition first" }
        val waiting=seats.getOrPut(id) { mutableMapOf() }
        require(waiting[userId]==null || waiting[userId]===session) { "This account has already joined from another connection" }
        checkFunds(c)
        session.privateChallengeId=id
        waiting[userId]=session
        c=update(c.copy(ready=waiting.keys.toList()))
        if(waiting.size==2) {
            try {
                val a=waiting.getValue(c.fromId);val b=waiting.getValue(c.toId)
                require(a.userId==c.fromId && b.userId==c.toId) { "Player identity changed; send a new invitation" }
                val match=lobby.startPrivate(a,b,c.game,roomId(c))
                require(match!=null) { "Not enough coins to start the match" }
                update(c.copy(status="playing",roomId=match))
                seats.remove(id)
            } catch(e:Exception) {
                if(lobby.isRoomActive(roomId(c))) {
                    records[id]=c.copy(status="playing",roomId=roomId(c))
                    seats.remove(id)
                    return@withLock
                }
                seats.remove(id)?.values?.forEach { it.privateChallengeId=null;it.send(ErrorMsg("challenge_error",e.message ?: "Private match could not start")) }
                update(c.copy(status="cancelled",ready=emptyList()))
                throw e
            }
        } else session.send(Queued(position=1,botFallbackMs=0))
    }
    suspend fun leave(session:PlayerSession,cancel:Boolean=false) = lock.withLock {
        seats.toMap().forEach { (id,waiting) ->
            val userId=session.userId
            if(userId!=null && waiting[userId]===session) {
                waiting.remove(userId)
                session.privateChallengeId=null
                val c=records.getValue(id)
                if(cancel) {
                    update(c.copy(status="cancelled",ready=emptyList()))
                    seats.remove(id)?.values?.forEach { it.privateChallengeId=null;it.send(ErrorMsg("private_cancelled","The invitation was cancelled")) }
                } else update(c.copy(ready=waiting.keys.toList()))
                if(waiting.isEmpty()) seats.remove(id)
            }
        }
    }
}

@Serializable private data class Challenge(
    val id:String,val fromId:String,val toId:String,val fromName:String,val toName:String,val game:String,val difficulty:String,
    val status:String,val createdAt:Long,val expiresAt:Long,val ready:List<String> = emptyList(),val roomId:String="",val reservedRoomId:String="",
)
