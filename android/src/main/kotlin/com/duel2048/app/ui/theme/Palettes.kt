package com.duel2048.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.duel2048.shared.engine.Rules

data class TileStyle(val start: Color, val end: Color, val text: Color = Color.White)

/** One visual theme. Tile gradients index by log2(value): 2, 4, 8 ... 4096+. */
data class DuelPalette(
    val id: String,
    val label: String,
    val bgTop: Color,
    val bgBottom: Color,
    val orbColors: List<Color>,
    val accent: Color,
    val accent2: Color,
    val tiles: List<TileStyle>,
    val surface: Color = Color.White.copy(alpha = 0.06f),
    val surfaceBorder: Color = Color.White.copy(alpha = 0.14f),
    val boardBg: Color = Color(0xFF000000).copy(alpha = 0.38f),
    val cellBg: Color = Color.White.copy(alpha = 0.055f),
    val textPrimary: Color = Color(0xFFF5F7FF),
    val textSecondary: Color = Color(0xFFA8B0CC),
    val danger: Color = Color(0xFFFF4D6D),
    val success: Color = Color(0xFF3DFF9A),
    val gold: Color = Color(0xFFFFD166),
    val garbageStart: Color = Color(0xFF3B4252),
    val garbageEnd: Color = Color(0xFF161A22),
    val garbageCrack: Color = Color(0xFF9AA3B8),
) {
    fun tileStyle(value: Int): TileStyle = tiles[(Rules.log2(value) - 1).coerceIn(0, tiles.lastIndex)]
}

object Palettes {
    private val dark = Color(0xFF12131A)

    val NEON = DuelPalette(
        id = "neon", label = "Neon",
        bgTop = Color(0xFF0E1440), bgBottom = Color(0xFF05070F),
        orbColors = listOf(Color(0xFF00D4E4), Color(0xFF5F4BFF), Color(0xFFFF2BD6)),
        accent = Color(0xFF00E5FF), accent2 = Color(0xFF7C5CFF),
        tiles = listOf(
            TileStyle(Color(0xFF3F47C9), Color(0xFF2B329C)),
            TileStyle(Color(0xFF6432E1), Color(0xFF481FB5)),
            TileStyle(Color(0xFF9E20F8), Color(0xFF720CB8)),
            TileStyle(Color(0xFFDC16DC), Color(0xFF9C0C9C)),
            TileStyle(Color(0xFFF52A6C), Color(0xFFB81448)),
            TileStyle(Color(0xFFFF4E20), Color(0xFFBD300A)),
            TileStyle(Color(0xFFFF9E00), Color(0xFFC47600)),
            TileStyle(Color(0xFFDADE00), Color(0xFF9CA000), dark),
            TileStyle(Color(0xFF30DA4E), Color(0xFF1A9E34)),
            TileStyle(Color(0xFF00D4E4), Color(0xFF008FA0)),
            TileStyle(Color(0xFFFFF858), Color(0xFFFFB700), dark),
            TileStyle(Color(0xFFFFFFFF), Color(0xFFFF007F), dark),
        ),
    )

    val SUNSET = DuelPalette(
        id = "sunset", label = "Sunset",
        bgTop = Color(0xFF3A0D3C), bgBottom = Color(0xFF12040F),
        orbColors = listOf(Color(0xFFFF007F), Color(0xFFFF8C00), Color(0xFFFFB703)),
        accent = Color(0xFFFF3D8F), accent2 = Color(0xFFFFB703),
        tiles = listOf(
            TileStyle(Color(0xFF581C87), Color(0xFF3B0764)),
            TileStyle(Color(0xFF86198F), Color(0xFF5B0D62)),
            TileStyle(Color(0xFFBE185D), Color(0xFF831843)),
            TileStyle(Color(0xFFE11D48), Color(0xFF9F1239)),
            TileStyle(Color(0xFFFF007F), Color(0xFFB30059)),
            TileStyle(Color(0xFFEA580C), Color(0xFF9A3412)),
            TileStyle(Color(0xFFF59E0B), Color(0xFF92400E)),
            TileStyle(Color(0xFF0284C7), Color(0xFF0369A1)),
            TileStyle(Color(0xFF06B6D4), Color(0xFF0891B2)),
            TileStyle(Color(0xFF00F0FF), Color(0xFF00A6B2), dark),
            TileStyle(Color(0xFFFFEA00), Color(0xFFFF007F), dark),
            TileStyle(Color(0xFFFFFFFF), Color(0xFF7B00FF), dark),
        ),
    )

    val OCEAN = DuelPalette(
        id = "ocean", label = "Ocean",
        bgTop = Color(0xFF06283D), bgBottom = Color(0xFF020B12),
        orbColors = listOf(Color(0xFF48CAE4), Color(0xFF0077B6), Color(0xFF00F5D4)),
        accent = Color(0xFF38BDF8), accent2 = Color(0xFF2DD4BF),
        tiles = listOf(
            TileStyle(Color(0xFF0C4A6E), Color(0xFF062B41)),
            TileStyle(Color(0xFF075985), Color(0xFF033A57)),
            TileStyle(Color(0xFF0369A1), Color(0xFF024A73)),
            TileStyle(Color(0xFF0284C7), Color(0xFF035B8A)),
            TileStyle(Color(0xFF0EA5E9), Color(0xFF0274AD)),
            TileStyle(Color(0xFF38BDF8), Color(0xFF0284C7)),
            TileStyle(Color(0xFF06B6D4), Color(0xFF0891B2)),
            TileStyle(Color(0xFF14B8A6), Color(0xFF0F766E)),
            TileStyle(Color(0xFF10B981), Color(0xFF047857)),
            TileStyle(Color(0xFF00B4D8), Color(0xFF0077B6)),
            TileStyle(Color(0xFFE0F2FE), Color(0xFF38BDF8), dark),
            TileStyle(Color(0xFFFFFFFF), Color(0xFF0284C7), dark),
        ),
    )

    val GALAXY = DuelPalette(
        id = "galaxy", label = "Galaxy",
        bgTop = Color(0xFF231046), bgBottom = Color(0xFF07030F),
        orbColors = listOf(Color(0xFFB580FF), Color(0xFF60D5FF), Color(0xFFF472B6)),
        accent = Color(0xFFC084FC), accent2 = Color(0xFF60D5FF),
        tiles = listOf(
            TileStyle(Color(0xFF312E81), Color(0xFF1E1B4B)),
            TileStyle(Color(0xFF4338CA), Color(0xFF2E268F)),
            TileStyle(Color(0xFF6D28D9), Color(0xFF4C1D95)),
            TileStyle(Color(0xFF8B5CF6), Color(0xFF5B21B6)),
            TileStyle(Color(0xFFA855F7), Color(0xFF7E22CE)),
            TileStyle(Color(0xFFD946EF), Color(0xFFA21CAF)),
            TileStyle(Color(0xFFEC4899), Color(0xFFBE185D)),
            TileStyle(Color(0xFF0EA5E9), Color(0xFF0369A1)),
            TileStyle(Color(0xFF06B6D4), Color(0xFF0E7490)),
            TileStyle(Color(0xFF38BDF8), Color(0xFF6366F1)),
            TileStyle(Color(0xFFF0ABFC), Color(0xFF818CF8), dark),
            TileStyle(Color(0xFFFFFFFF), Color(0xFFF0ABFC), dark),
        ),
    )

    val all = listOf(NEON, SUNSET, OCEAN, GALAXY)

    fun byId(id: String): DuelPalette = all.firstOrNull { it.id == id } ?: NEON
}
