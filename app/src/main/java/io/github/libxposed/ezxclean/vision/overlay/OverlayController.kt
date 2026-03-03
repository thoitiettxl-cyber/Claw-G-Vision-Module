package io.github.libxposed.ezxclean.vision.overlay

import android.app.Application
import android.os.Handler
import android.os.Looper
import io.github.libxposed.ezxclean.logE
import io.github.libxposed.ezxclean.logI
import io.github.libxposed.ezxclean.vision.model.OverlayConfig

object OverlayController {
    private lateinit var application: Application
    private lateinit var config: OverlayConfig
    private val handler = Handler(Looper.getMainLooper())

    fun initialize(app: Application, overlayConfig: OverlayConfig) {
        application = app
        config = overlayConfig
        logI("OverlayController initialized")
        handler.post { renderOverlay() }
    }

    private fun renderOverlay() {
        runCatching {
            logI("Rendering overlay layer: ${config.overlayLayer}")
        }.onFailure {
            logE("Overlay render failed", it)
        }
        handler.postDelayed({ renderOverlay() }, config.refreshIntervalMs)
    }
}
