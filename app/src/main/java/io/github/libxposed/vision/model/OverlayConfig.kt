package io.github.libxposed.vision.model

class OverlayConfig(
    val refreshIntervalMs: Long,
    val overlayLayer: String,
    val drawingEnabled: Boolean = true,
    val maxRenderNodes: Int = 32,
)
