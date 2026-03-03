package io.github.libxposed.vision.model

class ActionConfig(
    val dispatchGuard: DispatchGuard,
)

enum class DispatchGuard {
    STRICT,
    LENIENT
}
