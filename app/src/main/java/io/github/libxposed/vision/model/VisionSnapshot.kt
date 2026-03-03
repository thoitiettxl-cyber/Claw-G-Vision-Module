package io.github.libxposed.vision.model

data class VisionSnapshot(
    val timestampMs: Long,
    val packageName: String,
    val windowId: Int?,
    val nodes: List<VisionNode>,
    val focusedNodeId: String?,
    val meta: Map<String, String> = emptyMap(),
)
