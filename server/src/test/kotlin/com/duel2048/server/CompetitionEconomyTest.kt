package com.duel2048.server

import com.duel2048.server.db.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.test.*

class CompetitionEconomyTest {
    private fun checkEconomy(db:UserStore, dir:File) = runBlocking {
        val suffix=UUID.randomUUID().toString().take(5)
        val a=(db.register("A$suffix","pass1") as AuthResult.Ok).user
        val b=(db.register("B$suffix","pass2") as AuthResult.Ok).user
        val store=CompetitionStore(db,dir.path)
        val room="economy-$suffix"
        store.enter(room,"word_puzzle","pvp",listOf(a.id,b.id))
        assertEquals(90,store.wallet(a.id).coins)
        assertEquals(90,store.wallet(b.id).coins)
        store.enter(room,"word_puzzle","pvp",listOf(a.id,b.id))
        assertEquals(90,store.wallet(a.id).coins)
        val replicas=if(db is MySqlStore) List(3) { CompetitionStore(db,dir.path) } else listOf(store)
        val executor=Executors.newFixedThreadPool(3)
        try { executor.invokeAll((1..6).map { java.util.concurrent.Callable { replicas[it%replicas.size].settle(room,a.id,false) } }).forEach { it.get() } }
        finally { executor.shutdownNow() }
        assertEquals(115,store.wallet(a.id).coins)
        assertEquals(90,store.wallet(b.id).coins)
        store.enter("draw-$suffix","math","pvp",listOf(a.id,b.id))
        store.settle("draw-$suffix",null,true)
        assertEquals(115,store.wallet(a.id).coins)
        assertEquals(90,store.wallet(b.id).coins)
        for(i in 1..9) store.enter("debit-$suffix-$i","cube","pvp",listOf(b.id))
        assertEquals(0,store.wallet(b.id).coins)
        assertFailsWith<IllegalArgumentException> { store.enter("insufficient-$suffix","2048","pvp",listOf(a.id,b.id)) }
        assertEquals(115,store.wallet(a.id).coins)
        assertEquals(0,store.wallet(b.id).coins)
        val order=store.purchase(a.id,"order-$suffix",50)
        assertEquals("pending",order.status)
        assertEquals(order,store.purchase(a.id,"order-$suffix",50))
        assertEquals(115,store.wallet(a.id).coins)
        assertFailsWith<IllegalArgumentException> { store.purchase(a.id,"invalid-$suffix",9999) }
        store.checkpoint(room,"word_puzzle","pvp","over",a.name,b.name,8,3,a.name,"{}")
        store.checkpoint(room,"word_puzzle","pvp","playing",a.name,b.name,0,0,null,"{}")
        assertEquals("over",store.rooms().first { it.id==room }.status)
        store.enter("crash-$suffix","2048","pvp",listOf(a.id))
        store.checkpoint("crash-$suffix","2048","pvp","playing",a.name,"Bot",0,0,null,"{}")
        store.recoverInterruptedMatches()
        assertEquals(115,store.wallet(a.id).coins)
        store.recoverInterruptedMatches()
        assertEquals(115,store.wallet(a.id).coins)
        val restarted=CompetitionStore(db,dir.path)
        assertEquals(115,restarted.wallet(a.id).coins)
        restarted.settle(room,a.id,false)
        assertEquals(115,restarted.wallet(a.id).coins)
    }
    @Test fun `development ledger prevents double entry rewards and negative balances`() {
        val dir=Files.createTempDirectory("economy-ledger").toFile()
        try { checkEconomy(JsonDb(File(dir,"users.json")).load(),dir) } finally { dir.deleteRecursively() }
    }
    @Test fun `SQL ledger shares transactions and idempotency across store instances`() {
        val url=System.getenv("DUEL_TEST_DB_URL") ?: return
        require(url.substringBefore('?').endsWith("_test"))
        val dir=Files.createTempDirectory("economy-sql").toFile()
        val db=MySqlStore(url,System.getenv("DUEL_TEST_DB_USER"),System.getenv("DUEL_TEST_DB_PASSWORD"))
        try { checkEconomy(db,dir) } finally { db.close();dir.deleteRecursively() }
    }
}
