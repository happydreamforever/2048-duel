package com.duel2048.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duel2048.app.ui.theme.LocalLowEffects
import com.duel2048.app.ui.theme.LocalPalette
import kotlin.math.abs

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.surfaceBorder, shape)
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun NeonButton(
    text: String,
    modifier: Modifier = Modifier,
    colors: List<Color>? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    textColor: Color = Color.White,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val gradient = colors ?: listOf(palette.accent2, palette.accent)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "press")
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.5f }
            .drawBehind {
                val r = 18.dp.toPx()
                for (i in 1..3) {
                    val pad = i * 5.dp.toPx()
                    drawRoundRect(
                        gradient.last().copy(alpha = 0.16f / i),
                        topLeft = Offset(-pad, -pad),
                        size = Size(size.width + 2 * pad, size.height + 2 * pad),
                        cornerRadius = CornerRadius(r + pad),
                    )
                }
            }
            .clip(shape)
            .background(Brush.horizontalGradient(gradient))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = textColor, fontSize = 16.sp)
            if (subtitle != null) {
                Text(subtitle, color = textColor.copy(alpha = 0.8f), fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Title text with a light band sweeping across a gradient. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
fun ShimmerTitle(text: String, fontSize: TextUnit, modifier: Modifier = Modifier, letterSpacing: TextUnit = (-2).sp) {
    val palette = LocalPalette.current
    val low = LocalLowEffects.current
    val shift by rememberInfiniteTransition(label = "shimmer").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "s",
    )
    val brush = if (low) {
        Brush.linearGradient(listOf(palette.accent, palette.accent2))
    } else {
        Brush.linearGradient(
            listOf(palette.accent, Color.White, palette.accent2, palette.accent),
            start = Offset(shift * 900f - 500f, 0f),
            end = Offset(shift * 900f + 100f, 260f),
        )
    }
    Text(
        text,
        modifier = modifier,
        style = TextStyle(brush = brush, fontSize = fontSize, fontWeight = FontWeight.Black, letterSpacing = letterSpacing),
    )
}

/** Number that counts up and pops when it changes. */
@Composable
fun ScoreCounter(value: Int, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.headlineMedium, color: Color = LocalPalette.current.textPrimary) {
    val animated by animateIntAsState(value, tween(320), label = "score")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value > 0) {
            pop.snapTo(1.16f)
            pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        }
    }
    Text(
        animated.toString(),
        modifier = modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        style = style,
        color = color,
        maxLines = 1,
    )
}

@Composable
fun Avatar(name: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, isBot: Boolean = false) {
    val palette = LocalPalette.current
    val h = abs(name.hashCode())
    val c1 = palette.orbColors[h % palette.orbColors.size]
    val c2 = palette.tiles[(h / 7) % palette.tiles.size].start
    Box(
        modifier
            .size(size)
            .drawBehind {
                drawCircle(c1.copy(alpha = 0.35f), radius = this.size.minDimension * 0.62f)
            }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c1, c2)))
            .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (isBot) "AI" else name.trim().take(1).uppercase().ifEmpty { "?" },
            fontWeight = FontWeight.Black,
            color = Color.White,
            fontSize = (size.value * 0.4f).sp,
        )
    }
}

@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = LocalPalette.current.textPrimary) {
    val palette = LocalPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.surfaceBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, maxLines = 1)
    }
}

@Composable
fun StatRow(label: String, value: String, valueColor: Color = LocalPalette.current.textPrimary) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, maxLines = 1)
    }
}

fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
