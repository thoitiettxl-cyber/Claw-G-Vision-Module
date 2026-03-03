package io.github.libxposed.vision.bridge

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.annotations.XposedHooker
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.AfterInvocation
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object Xposed {
    @Volatile
    lateinit var xposedInterface: XposedInterface
        private set
    
    fun init(xposed: XposedInterface) {
        xposedInterface = xposed
    }
    
    val isInitialized: Boolean
        get() = ::xposedInterface.isInitialized
    
    fun hookMethod(method: Method, callback: MethodHookCallback): Unhook<Method> {
        CallbackRegistry.register(method, callback)
        val unhooker = xposedInterface.hook(method, DynamicHooker::class.java)
        return Unhook(unhooker)
    }
    
    fun hookConstructor(constructor: Constructor<*>, callback: MethodHookCallback): Unhook<Constructor<*>> {
        CallbackRegistry.register(constructor, callback)
        val unhooker = xposedInterface.hook(constructor, DynamicHooker::class.java)
        return Unhook(unhooker as XposedInterface.MethodUnhooker<Constructor<*>>)
    }
    
    fun hookAllMethods(clazz: Class<*>, name: String, callback: MethodHookCallback): Set<Unhook<Method>> {
        return clazz.declaredMethods
            .filter { it.name == name }
            .onEach { it.isAccessible = true }
            .map { hookMethod(it, callback) }
            .toMutableSet()
    }
    
    fun hookAllConstructors(clazz: Class<*>, callback: MethodHookCallback): Set<Unhook<Constructor<*>>> {
        return clazz.declaredConstructors
            .onEach { it.isAccessible = true }
            .map { hookConstructor(it, callback) }
            .toMutableSet()
    }
    
    fun findMethodExact(clazz: Class<*>, name: String, vararg paramTypes: Class<*>): Method {
        return clazz.getDeclaredMethod(name, *paramTypes).apply { isAccessible = true }
    }
    
    fun findConstructorExact(clazz: Class<*>, vararg paramTypes: Class<*>): Constructor<*> {
        return clazz.getDeclaredConstructor(*paramTypes).apply { isAccessible = true }
    }
    
    fun deoptimizeMethod(method: Method) {
        xposedInterface.deoptimize(method)
    }
    
    @Suppress("UNCHECKED_CAST")
    fun <T> invokeOriginal(method: Method, thisObject: Any?, vararg args: Any?): T {
        return xposedInterface.invokeOrigin(method, thisObject, *args) as T
    }
    
    fun log(msg: String) = xposedInterface.log(msg)
    fun log(msg: String, t: Throwable) = xposedInterface.log(msg, t)
}

internal object CallbackRegistry {
    private val callbacks = ConcurrentHashMap<Member, MethodHookCallback>()
    
    fun register(member: Member, callback: MethodHookCallback) {
        callbacks[member] = callback
    }
    
    fun get(member: Member): MethodHookCallback? = callbacks[member]
    
    fun remove(member: Member) {
        callbacks.remove(member)
    }
}

@XposedHooker
class DynamicHooker : Hooker {
    companion object {
        @BeforeInvocation
        @JvmStatic
        fun before(callback: BeforeHookCallback) {
            CallbackRegistry.get(callback.member)?.let { methodCallback ->
                try {
                    methodCallback.beforeHook(HookParam.fromBefore(callback))
                } catch (t: Throwable) {
                    Xposed.log("Error in beforeHook: ${callback.member}", t)
                }
            }
        }
        
        @AfterInvocation
        @JvmStatic
        fun after(callback: AfterHookCallback) {
            CallbackRegistry.get(callback.member)?.let { methodCallback ->
                try {
                    methodCallback.afterHook(HookParam.fromAfter(callback))
                } catch (t: Throwable) {
                    Xposed.log("Error in afterHook: ${callback.member}", t)
                }
            }
        }
    }
}
