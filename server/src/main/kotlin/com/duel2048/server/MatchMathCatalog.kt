package com.duel2048.server

import com.duel2048.server.db.MySqlStore
import com.duel2048.server.db.UserStore
import com.duel2048.shared.protocol.Protocol
import com.duel2048.shared.social.MathProblem
import com.duel2048.shared.social.MathRules
import com.duel2048.shared.social.PuzzleCatalog
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** SQL publication takes effect for new rooms; existing rooms retain their checkpoint. */
class MatchMathCatalog(private val db: UserStore, private val packaged: PuzzleCatalog) {
    fun choose(level: String): MathProblem {
        if (db !is MySqlStore) return packaged.math(level)
        return db.connection { c ->
            val managed = c.prepareStatement("SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='math_problems'").use { ps -> ps.executeQuery().use { it.next() } }
            if (!managed) return@connection packaged.math(level)
            val entries = c.prepareStatement("SELECT id,prompt,answer,choices_json FROM math_problems p WHERE difficulty=? AND state='active' AND NOT EXISTS (SELECT 1 FROM admin_deleted_records t WHERE t.entity_type='math' AND t.record_id=p.id AND t.deleted_at IS NOT NULL)").use { ps ->
                ps.setString(1,level)
                ps.executeQuery().use { rs -> buildList {
                    while (rs.next()) {
                        runCatching {
                            MathRules.validate(level,MathProblem(rs.getString("id"),rs.getString("prompt"),rs.getString("answer"),Protocol.json.parseToJsonElement(rs.getString("choices_json")).jsonArray.map { it.jsonPrimitive.content }))
                        }.getOrNull()?.let { add(it) }
                    }
                } }
            }
            require(entries.isNotEmpty()) { "No valid published math problems at this level. Ask an administrator to publish a set" }
            entries.random().let { it.copy(choices=it.choices.shuffled()) }
        }
    }
}
