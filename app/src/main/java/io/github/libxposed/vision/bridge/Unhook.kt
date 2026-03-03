package io.github.libxposed.vision.bridge

import io.github.libxposed.api.XposedInterface.MethodUnhooker
import java.lang.reflect.Member

class Unhook<T : Member>(private val inner: MethodUnhooker<T>) {
    val hookedMethod: T get() = inner.origin
    fun unhook() = inner.unhook()
}
