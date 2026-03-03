@file:Suppress("NOTHING_TO_INLINE")

package io.github.libxposed.ezxclean.arch

import io.github.libxposed.ezxclean.bridge.HookParam
import io.github.libxposed.ezxclean.bridge.MethodHookCallback
import io.github.libxposed.ezxclean.bridge.Unhook
import io.github.libxposed.ezxclean.bridge.Xposed
import java.lang.reflect.Constructor
import java.lang.reflect.Method

typealias HookCallback = (HookParam) -> Unit
typealias HookReplacement = (HookParam) -> Any?

// Method extensions
inline fun Method.hookBefore(crossinline cond: () -> Boolean = { true }, crossinline fn: HookCallback): Unhook<Method> =
    Xposed.hookMethod(this, object : MethodHookCallback() {
        override fun beforeHook(param: HookParam) { if (cond()) fn(param) }
    })

inline fun Method.hookAfter(crossinline cond: () -> Boolean = { true }, crossinline fn: HookCallback): Unhook<Method> =
    Xposed.hookMethod(this, object : MethodHookCallback() {
        override fun afterHook(param: HookParam) { if (cond()) fn(param) }
    })

inline fun Method.hookReplace(crossinline cond: () -> Boolean = { true }, crossinline replacement: HookReplacement): Unhook<Method> =
    Xposed.hookMethod(this, object : MethodHookCallback() {
        override fun beforeHook(param: HookParam) {
            if (cond()) { try { param.result = replacement(param) } catch (t: Throwable) { param.throwable = t } }
        }
    })

fun Method.hookConstant(constant: Any?): Unhook<Method> = hookReplace { constant }
fun Method.hookNop(): Unhook<Method> = hookConstant(null)

// Class.method extensions
inline fun Class<*>.hookBefore(name: String, vararg types: Class<*>, crossinline cond: () -> Boolean = { true }, crossinline fn: HookCallback): Unhook<Method> =
    Xposed.findMethodExact(this, name, *types).hookBefore(cond, fn)

inline fun Class<*>.hookAfter(name: String, vararg types: Class<*>, crossinline cond: () -> Boolean = { true }, crossinline fn: HookCallback): Unhook<Method> =
    Xposed.findMethodExact(this, name, *types).hookAfter(cond, fn)

inline fun Class<*>.hookReplace(name: String, vararg types: Class<*>, crossinline cond: () -> Boolean = { true }, crossinline replacement: HookReplacement): Unhook<Method> =
    Xposed.findMethodExact(this, name, *types).hookReplace(cond, replacement)

fun Class<*>.hookConstant(name: String, vararg types: Class<*>, constant: Any?): Unhook<Method> =
    Xposed.findMethodExact(this, name, *types).hookConstant(constant)

fun Class<*>.hookNop(name: String, vararg types: Class<*>): Unhook<Method> =
    Xposed.findMethodExact(this, name, *types).hookNop()

// Constructor extensions
inline fun Class<*>.hookCBefore(vararg types: Class<*>, crossinline cond: () -> Boolean = { true }, crossinline fn: HookCallback): Unhook<Constructor<*>> =
    Xposed.findConstructorExact(this, *types).let { c ->
        Xposed.hookConstructor(c, object : MethodHookCallback() {
            override fun beforeHook(param: HookParam) { if (cond()) fn(param) }
        })
    }

inline fun Class<*>.hookCAfter(vararg types: Class<*>, crossinline cond: () -> Boolean = { true }, crossinline fn: HookCallback): Unhook<Constructor<*>> =
    Xposed.findConstructorExact(this, *types).let { c ->
        Xposed.hookConstructor(c, object : MethodHookCallback() {
            override fun afterHook(param: HookParam) { if (cond()) fn(param) }
        })
    }

// Hook all methods
inline fun Class<*>.hookAllBefore(name: String, crossinline cond: () -> Boolean = { true }, crossinline fn: HookCallback): Set<Unhook<Method>> =
    Xposed.hookAllMethods(this, name, object : MethodHookCallback() {
        override fun beforeHook(param: HookParam) { if (cond()) fn(param) }
    })

inline fun Class<*>.hookAllAfter(name: String, crossinline cond: () -> Boolean = { true }, crossinline fn: HookCallback): Set<Unhook<Method>> =
    Xposed.hookAllMethods(this, name, object : MethodHookCallback() {
        override fun afterHook(param: HookParam) { if (cond()) fn(param) }
    })

inline fun Class<*>.hookAllReplace(name: String, crossinline cond: () -> Boolean = { true }, crossinline replacement: HookReplacement): Set<Unhook<Method>> =
    Xposed.hookAllMethods(this, name, object : MethodHookCallback() {
        override fun beforeHook(param: HookParam) {
            if (cond()) { try { param.result = replacement(param) } catch (t: Throwable) { param.throwable = t } }
        }
    })

fun Class<*>.hookAllConstant(name: String, constant: Any?): Set<Unhook<Method>> =
    hookAllReplace(name) { constant }

fun Class<*>.hookAllNop(name: String): Set<Unhook<Method>> =
    hookAllConstant(name, null)

inline fun Class<*>.hookAllNopIf(name: String, crossinline cond: () -> Boolean): Set<Unhook<Method>> =
    hookAllBefore(name) { param -> if (cond()) param.result = null }

// Hook all constructors
inline fun Class<*>.hookAllCBefore(crossinline cond: () -> Boolean = { true }, crossinline fn: HookCallback): Set<Unhook<Constructor<*>>> =
    Xposed.hookAllConstructors(this, object : MethodHookCallback() {
        override fun beforeHook(param: HookParam) { if (cond()) fn(param) }
    })

inline fun Class<*>.hookAllCAfter(crossinline cond: () -> Boolean = { true }, crossinline fn: HookCallback): Set<Unhook<Constructor<*>>> =
    Xposed.hookAllConstructors(this, object : MethodHookCallback() {
        override fun afterHook(param: HookParam) { if (cond()) fn(param) }
    })

// ClassLoader extensions
fun ClassLoader.findClassN(name: String): Class<*>? = runCatching { loadClass(name) }.getOrNull()
fun ClassLoader.findClass(name: String): Class<*> = loadClass(name)

fun ClassLoader.findClassOf(vararg names: String): Class<*> {
    for (name in names) {
        findClassN(name)?.let { return it }
    }
    error("None of classes found: ${names.joinToString(",")}")
}
