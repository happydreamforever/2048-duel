package com.duel2048.app.data

/** Which 3D cube engine renders cube PvP / training screens. */
enum class CubeRenderer(val id: String) {
    /** Ported MagicCube-Android OpenGL stack (package `cube/magic`). */
    MAGIC("magic"),
    /** Vendored AnimCubeAndroid (`:cube2` module). */
    CUBE2("cube2"),
    ;

    companion object {
        fun fromId(id: String): CubeRenderer =
            entries.firstOrNull { it.id == id } ?: CUBE2
    }
}
