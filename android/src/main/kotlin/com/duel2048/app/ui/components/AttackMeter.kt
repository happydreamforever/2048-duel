package com.duel2048.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.duel2048.app.ui.theme.LocalPalette
import com.duel2048.shared.engine.Rules

/** Gradient attack meter that pulses when an attack is almost ready. */
@Composable
fun AttackMeter(energy: Int, modifier: Modifier = Modifier, label: String = "ATTACK", compact: Boolean = false) {
    val palette = LocalPalette.current
    val target = (energy.toFloat() / Rules.ATTACK_COST).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(target, spring(stiffness = Spring.StiffnessLow), label = "meter")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "p",
    )
    Column(modifier) {
        if (!compact) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                Text("$energy / ${Rules.ATTACK_COST}", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(if (compact) 7.dp else 14.dp)) {
            val cr = CornerRadius(size.height / 2f)
            drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = cr)
            drawRoundRect(palette.surfaceBorder, cornerRadius = cr, style = Stroke(1.dp.toPx()))
            if (fraction > 0.01f) {
                val w = size.width * fraction
                val hot = fraction > 0.72f
                val glowA = if (hot) 0.35f + 0.4f * pulse else 0.18f
                val tip = if (hot) palette.danger else palette.accent
                drawRoundRect(
                    Brush.horizontalGradient(listOf(palette.accent2, palette.accent, tip), endX = w.coerceAtLeast(1f)),
                    size = Size(w, size.height),
                    cornerRadius = cr,
                )
                drawRoundRect(Color.White.copy(alpha = 0.22f), size = Size(w, size.height / 2f), cornerRadius = cr)
                drawRoundRect(
                    tip.copy(alpha = glowA),
                    topLeft = Offset(-2.dp.toPx(), -2.dp.toPx()),
                    size = Size(w + 4.dp.toPx(), size.height + 4.dp.toPx()),
                    cornerRadius = CornerRadius(size.height),
                    style = Stroke(2.dp.toPx()),
                )
            }
        }
    }
}
