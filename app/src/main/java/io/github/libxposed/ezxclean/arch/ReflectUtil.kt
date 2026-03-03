@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")

package io.github.libxposed.ezxclean.arch

import io.github.libxposed.ezxclean.bridge.Xposed
import java.lang.reflect.*

// ══════════════════════════════════════════════════════════════════
// Instance Field Access
// ══════════════════════════════════════════════════════════════════

/** Lấy field value. */
inline fun Any?.getObj(name: String): Any? {
    val field = this!!.javaClass.findField(name)
    return field.get(this)
}

/** Set field value. */
inline fun Any?.setObj(name: String, value: Any?) {
    val field = this!!.javaClass.findField(name)
    field.set(this, value)
}

/** Lấy và cast. */
inline fun <T> Any?.getObjAs(name: String): T = getObj(name) as T

/** Lấy và safe cast (nullable). */
inline fun <T> Any?.getObjAsN(name: String): T? = getObj(name) as? T

// ══════════════════════════════════════════════════════════════════
// Static Field Access
// ══════════════════════════════════════════════════════════════════

inline fun Class<*>.getObjS(name: String): Any? {
    val field = findField(name)
    return field.get(null)
}

inline fun Class<*>.setObjS(name: String, value: Any?) {
    val field = findField(name)
    field.set(null, value)
}

inline fun <T> Class<*>.getObjSAs(name: String): T = getObjS(name) as T

inline fun <T> Class<*>.getObjSAsN(name: String): T? = getObjS(name) as? T

// ══════════════════════════════════════════════════════════════════
// Method Invocation
// ══════════════════════════════════════════════════════════════════

/** Gọi instance method. */
inline fun Any?.call(name: String, vararg args: Any?): Any? {
    val clazz = this!!.javaClass
    val method = clazz.declaredMethods.find { 
        it.name == name && it.parameterCount == args.size 
    } ?: error("Method not found: $name")
    method.isAccessible = true
    return method.invoke(this, *args)
}

/** Gọi static method. */
inline fun Class<*>.callS(name: String, vararg args: Any?): Any? {
    val method = declaredMethods.find { 
        it.name == name && it.parameterCount == args.size 
    } ?: error("Static method not found: $name")
    method.isAccessible = true
    return method.invoke(null, *args)
}

// ══════════════════════════════════════════════════════════════════
// Instance Creation
// ══════════════════════════════════════════════════════════════════

inline fun Class<*>.newInst(vararg args: Any?): Any {
    val constructor = declaredConstructors.find { 
        it.parameterCount == args.size 
    } ?: error("Constructor not found for ${args.size} args")
    constructor.isAccessible = true
    return constructor.newInstance(*args)
}

inline fun <T> Class<*>.newInstAs(vararg args: Any?): T = newInst(*args) as T

// ══════════════════════════════════════════════════════════════════
// Field Finding (recursive)
// ══════════════════════════════════════════════════════════════════

fun Class<*>.findField(name: String): Field {
    var clazz: Class<*>? = this
    while (clazz != null) {
        try {
            val field = clazz.getDeclaredField(name)
            field.isAccessible = true
            return field
        } catch (_: NoSuchFieldException) {
            clazz = clazz.superclass
        }
    }
    error("Field not found: $name in ${this.name}")
}

// ══════════════════════════════════════════════════════════════════
// Method Utilities
// ══════════════════════════════════════════════════════════════════

/** Force method chạy trong interpreter mode. */
inline fun Method.deoptimize() = Xposed.deoptimizeMethod(this)

fun Class<*>.deoptimize(name: String) {
    declaredMethods.forEach { if (it.name == name) it.deoptimize() }
}

/** Gọi original method trong hook callback. */
inline fun <T> Method.callOrig(receiver: Any?, vararg args: Any?): T =
    Xposed.invokeOriginal(this, receiver, *args)
