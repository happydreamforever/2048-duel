package com.duel2048.app.ui.components

import com.duel2048.app.fx.GameFx
import com.duel2048.app.game.GameEffect
import com.duel2048.app.ui.theme.DuelPalette

/** Localized templates for floating texts; "%1$d" placeholders are filled with String.format. */
data class FxStrings(val combo: String, val attack: String, val garbage: String, val shattered: String)

/** Maps engine/match events to particles, texts, shakes, sounds and haptics. */
fun handleEffect(e: GameEffect, mine: BoardFx, opp: BoardFx?, gfx: GameFx, palette: DuelPalette, strings: FxStrings) {
    when (e) {
        is GameEffect.Merges -> {
            val fx = if (e.mine) mine else opp ?: return
            val best = e.cells.maxOf { it.second }
            val intensity = (if (e.mine) 1f else 0.5f) *
                (if (best >= 128) 1.7f else if (best >= 32) 1.25f else 0.9f) *
                (if (e.combo >= 2) 1.3f else 1f)
            e.cells.forEach { (cell, value) -> fx.burstAt(cell, palette.tileStyle(value).start, intensity) }
            if (e.mine) {
                fx.text("+${e.scoreGained}", palette.textPrimary, e.cells.first().first, if (best >= 128) 1.3f else 1f)
                if (e.combo >= 2) {
                    fx.text(strings.combo.format(e.combo), palette.gold, null, 1.5f)
                    fx.shake(0.35f)
                    gfx.sound.combo(e.combo)
                    gfx.haptics.combo()
                } else {
                    gfx.sound.merge(best)
                    gfx.haptics.merge(best)
                }
            }
        }
        is GameEffect.Milestone -> {
            mine.text("${e.value}!", palette.gold, null, 1.9f)
            mine.flash(palette.gold)
        }
        is GameEffect.AttackSent -> {
            mine.text(strings.attack.format(e.count), palette.danger, null, 1.4f)
            opp?.flash(palette.danger)
            gfx.sound.attack()
            gfx.haptics.tick()
        }
        is GameEffect.GarbageHit -> {
            if (e.mine) {
                mine.shake(1f)
                mine.flash(palette.danger)
                e.cells.forEach { mine.burstAt(it, palette.danger, 0.8f) }
                mine.text(strings.garbage.format(e.cells.size), palette.danger, null, 1.2f)
                gfx.haptics.heavy()
                gfx.sound.hit()
            } else {
                opp?.shake(0.6f)
                e.cells.forEach { opp?.burstAt(it, palette.danger, 0.5f) }
            }
        }
        is GameEffect.GarbageShattered -> {
            val fx = if (e.mine) mine else opp ?: return
            e.cells.forEach { fx.burstAt(it, palette.garbageCrack, 1.3f) }
            if (e.mine) {
                fx.text(strings.shattered, palette.textPrimary, e.cells.first(), 1f)
                gfx.sound.shatter()
                gfx.haptics.tick()
            }
        }
        is GameEffect.GarbageDamaged -> {
            val fx = if (e.mine) mine else opp ?: return
            e.cells.forEach { fx.burstAt(it, palette.garbageCrack, 0.45f) }
        }
        is GameEffect.NoMove -> {
            mine.nudge(e.direction)
            gfx.sound.bump()
        }
        is GameEffect.CountdownTick -> gfx.sound.tick()
        GameEffect.Go -> {
            gfx.sound.go()
            gfx.haptics.success()
        }
        is GameEffect.MatchEnd -> {
            if (e.won) {
                gfx.sound.win()
                gfx.haptics.success()
            } else if (!e.draw) {
                gfx.sound.lose()
                gfx.haptics.fail()
            }
        }
        is GameEffect.Spawned -> Unit
    }
}
