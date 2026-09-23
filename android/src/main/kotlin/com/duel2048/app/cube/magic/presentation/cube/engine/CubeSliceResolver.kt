package com.duel2048.app.cube.magic.presentation.cube.engine

import com.duel2048.app.cube.magic.grafic.ActiveSlice

class CubeSliceResolver {
    fun shouldRotateX(activeSlice: ActiveSlice, index: Int): Boolean = when (index) {
        0 -> activeSlice == ActiveSlice.ROTATION_AXIS_X_0
        1 -> activeSlice == ActiveSlice.ROTATION_AXIS_X_1
        2 -> activeSlice == ActiveSlice.ROTATION_AXIS_X_2
        else -> false
    }

    fun shouldRotateY(activeSlice: ActiveSlice, index: Int): Boolean = when (index) {
        0 -> activeSlice == ActiveSlice.ROTATION_AXIS_Z_0
        1 -> activeSlice == ActiveSlice.ROTATION_AXIS_Z_1
        2 -> activeSlice == ActiveSlice.ROTATION_AXIS_Z_2
        else -> false
    }

    fun shouldRotateZ(activeSlice: ActiveSlice, index: Int): Boolean = when (index) {
        0 -> activeSlice == ActiveSlice.ROTATION_AXIS_Y_0
        1 -> activeSlice == ActiveSlice.ROTATION_AXIS_Y_1
        2 -> activeSlice == ActiveSlice.ROTATION_AXIS_Y_2
        else -> false
    }
}
