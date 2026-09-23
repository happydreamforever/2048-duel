package com.duel2048.app.cube.magic.presentation.cube

import android.util.Log
import com.duel2048.app.cube.magic.domain.cube.CubeLogger

class AndroidCubeLogger : CubeLogger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }
}
