package io.github.libxposed.ezxclean.vision.model

data class VisionRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

data class VisionNode(
    val id: String,
    val index: Int,
    val className: String?,
    val text: String?,
    val contentDesc: String?,
    val bounds: VisionRect,
    val visible: Boolean,
    val enabled: Boolean,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val depth: Int,
)
