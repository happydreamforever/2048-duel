@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.duel2048.app.ui.landscape

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.duel2048.app.*
import com.duel2048.app.R
import com.duel2048.app.ui.landscape.LandscapeText as Text
import com.duel2048.app.ui.theme.*

val LocalHubOverlay = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun Art(game: HubGame? = null, modifier: Modifier = Modifier) {
    val id =
        when (game) {
            HubGame.TILES -> R.drawable.art_tiles
            HubGame.CUBE -> R.drawable.art_cube
            HubGame.MATH -> R.drawable.art_math
            HubGame.WORD -> R.drawable.art_word
            HubGame.WORD_PUZZLE -> R.drawable.art_grid
            null -> R.drawable.art_robot
        }
    val low = LocalLowEffects.current
    val drift =
        if (low) null
        else
            rememberInfiniteTransition(label = "art drift")
                .animateFloat(
                    -2f,
                    2f,
                    infiniteRepeatable(
                        tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "art offset")
    Image(
        painterResource(id),
        contentDescription = null,
        modifier = modifier.graphicsLayer { translationY = drift?.value ?: 0f })
}

fun gameAccent(game: HubGame) =
    when (game) {
        HubGame.TILES -> Color(0xFFAC56FF)
        HubGame.CUBE -> Color(0xFFFF5C9D)
        HubGame.MATH -> Color(0xFF16D5FF)
        HubGame.WORD -> Color(0xFFFFBF46)
        HubGame.WORD_PUZZLE -> Color(0xFF00DFC5)
    }

@Composable
fun NeonPane(
    modifier: Modifier = Modifier,
    accent: Color = LocalPalette.current.accent,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(lerp(p.bgTop, accent, .07f), lerp(p.bgBottom, accent, .04f))))
            .border(
                1.dp,
                Brush.linearGradient(listOf(accent.copy(alpha = .7f), p.accent2.copy(alpha = .3f))),
                RoundedCornerShape(16.dp))
            .padding(12.dp),
        content = content)
}

@Composable
fun ArcadeAction(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = LocalPalette.current.accent,
    onClick: () -> Unit
) {
    val low = LocalLowEffects.current
    val interaction = remember {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    val pressed by interaction.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed && !low) .96f else 1f, tween(if (low) 0 else 120), label = "action press")
    Button(
        onClick,
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = 48.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = .62f),
                        LocalPalette.current.accent2.copy(alpha = .62f))),
                RoundedCornerShape(14.dp)),
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent, contentColor = Color.White),
        border = BorderStroke(1.dp, accent.copy(alpha = .75f))) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold)
        }
}

@Composable
fun UtilityHeader(title: String, onClose: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("‹  Dashboard", color = p.accent) }
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = p.textPrimary,
                fontWeight = FontWeight.Bold)
            TextButton(onClick = onClose) { Text("×", fontSize = 24.sp, color = p.textSecondary) }
        }
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    NeonPane(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalPalette.current.textSecondary)
        Text(
            value,
            color = LocalPalette.current.textPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1)
    }
}

@Composable
fun EmptyState(title: String, detail: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxSize().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Art(modifier = Modifier.weight(.35f).fillMaxHeight())
            Column(Modifier.weight(.65f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    title,
                    color = LocalPalette.current.textPrimary,
                    style = MaterialTheme.typography.headlineSmall)
                Text(detail, color = LocalPalette.current.textSecondary)
                if (action != null) ArcadeAction(action, onClick = onAction)
            }
        }
}
