package com.example.game.render

import android.content.Context
import android.opengl.GLSurfaceView
import com.example.game.engine.MatchEngine

class GameSurfaceView(
    context: Context,
    engine: MatchEngine,
    onRenderError: (Throwable) -> Unit
) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(ZoneZeroRenderer(engine, onRenderError))
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
