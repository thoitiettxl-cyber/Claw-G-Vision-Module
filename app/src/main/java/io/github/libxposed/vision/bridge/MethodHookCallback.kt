package io.github.libxposed.vision.bridge

abstract class MethodHookCallback {
    open fun beforeHook(param: HookParam) {}
    open fun afterHook(param: HookParam) {}
}
