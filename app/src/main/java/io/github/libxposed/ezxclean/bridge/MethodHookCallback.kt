package io.github.libxposed.ezxclean.bridge

abstract class MethodHookCallback {
    open fun beforeHook(param: HookParam) {}
    open fun afterHook(param: HookParam) {}
}
