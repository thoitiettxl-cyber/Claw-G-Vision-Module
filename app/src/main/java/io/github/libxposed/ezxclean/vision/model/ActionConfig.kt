package io.github.libxposed.ezxclean.vision.model

class ActionConfig(
    val dispatchGuard: DispatchGuard,
)

enum class DispatchGuard {
    STRICT,
    LENIENT
}
