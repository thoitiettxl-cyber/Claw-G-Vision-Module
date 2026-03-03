package io.github.libxposed.vision.model

import android.app.Application

class VisionConfig private constructor(
    val overlayConfig: OverlayConfig,
    val actionConfig: ActionConfig
) {
    companion object {
        fun default(): VisionConfig = VisionConfig(
            OverlayConfig(
                refreshIntervalMs = 250L,
                overlayLayer = "vision",
            ),
            ActionConfig(
                dispatchGuard = DispatchGuard.STRICT
            )
        )
    }
}
