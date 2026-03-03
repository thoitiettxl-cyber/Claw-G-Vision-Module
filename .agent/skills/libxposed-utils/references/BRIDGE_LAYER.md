# Bridge Layer - Chi tiết Kỹ thuật (LibXposed API 100)

> Lớp trừu tượng hóa LibXposed API 100, cô lập dependency và cung cấp DSL-friendly wrappers.

## Kiến trúc Tổng quan

```
┌─────────────────────────────────────────────────────────────────┐
│                     Application Code                             │
│  (IHook implementations, Handlers, Features)                     │
├─────────────────────────────────────────────────────────────────┤
│                       Bridge Layer                               │
│  Xposed.kt │ LoadPackageParam │ HookParam │ MethodHookCallback  │
│  DynamicHooker (với R8 Annotations)                             │
├─────────────────────────────────────────────────────────────────┤
│                    LibXposed API 100                             │
│  XposedModule │ XposedInterface │ Hooker │ BeforeHookCallback   │
└─────────────────────────────────────────────────────────────────┘
```

## Lý do Cần Bridge Layer

1. **DSL-Friendly API**: Kotlin extensions cho hooking (hookAfter, hookReplace, etc.)
2. **Cô lập Dependency**: Business logic không import trực tiếp `io.github.libxposed.api.*`
3. **Callback Registry**: Map dynamic callbacks tới static Hooker methods
4. **Type Safety**: Wrapper classes cung cấp type-safe API với Kotlin properties

---

## Xposed.kt - Core Wrapper

```kotlin
package [package].bridge

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

/**
 * Wrapper cho LibXposed API 100.
 * - Cung cấp DSL-friendly hooks
 * - Quản lý callback registry cho DynamicHooker
 * - Thread-safe với ConcurrentHashMap
 */
object Xposed {
    @Volatile
    lateinit var xposedInterface: XposedInterface
        private set
    
    /**
     * Initialize với XposedInterface từ ModuleMain.
     * Phải gọi trong constructor của XposedModule.
     */
    fun init(xposed: XposedInterface) {
        xposedInterface = xposed
    }
    
    val isInitialized: Boolean
        get() = ::xposedInterface.isInitialized
    
    // ========== Hooking Methods ==========
    
    /**
     * Hook method với callback.
     * Sử dụng DynamicHooker pattern với CallbackRegistry.
     */
    fun hookMethod(method: Method, callback: MethodHookCallback): Unhook<Method> {
        CallbackRegistry.register(method, callback)
        val unhooker = xposedInterface.hook(method, DynamicHooker::class.java)
        return Unhook(unhooker)
    }
    
    /**
     * Hook constructor với callback.
     */
    fun hookConstructor(constructor: Constructor<*>, callback: MethodHookCallback): Unhook<Constructor<*>> {
        CallbackRegistry.register(constructor, callback)
        val unhooker = xposedInterface.hook(constructor, DynamicHooker::class.java)
        @Suppress("UNCHECKED_CAST")
        return Unhook(unhooker as XposedInterface.MethodUnhooker<Constructor<*>>)
    }
    
    /**
     * Hook tất cả methods cùng tên trong một class.
     */
    fun hookAllMethods(clazz: Class<*>, name: String, callback: MethodHookCallback): Set<Unhook<Method>> {
        return clazz.declaredMethods
            .filter { it.name == name }
            .onEach { it.isAccessible = true }
            .map { hookMethod(it, callback) }
            .toMutableSet()
    }
    
    /**
     * Hook tất cả constructors của một class.
     */
    fun hookAllConstructors(clazz: Class<*>, callback: MethodHookCallback): Set<Unhook<Constructor<*>>> {
        return clazz.declaredConstructors
            .onEach { it.isAccessible = true }
            .map { hookConstructor(it, callback) }
            .toMutableSet()
    }
    
    // ========== Method Finding ==========
    
    fun findMethodExact(clazz: Class<*>, name: String, vararg paramTypes: Class<*>): Method {
        return clazz.getDeclaredMethod(name, *paramTypes).apply { isAccessible = true }
    }
    
    fun findConstructorExact(clazz: Class<*>, vararg paramTypes: Class<*>): Constructor<*> {
        return clazz.getDeclaredConstructor(*paramTypes).apply { isAccessible = true }
    }
    
    // ========== Advanced ==========
    
    /**
     * Force method chạy trong interpreter mode.
     * Prevent JIT inline, cần thiết cho một số hooks phức tạp.
     */
    fun deoptimizeMethod(method: Method) {
        xposedInterface.deoptimize(method)
    }
    
    /**
     * Gọi method gốc (bypass hooks).
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> invokeOriginal(method: Method, thisObject: Any?, vararg args: Any?): T {
        return xposedInterface.invokeOrigin(method, thisObject, *args) as T
    }
    
    // ========== Logging ==========
    
    fun log(msg: String) = xposedInterface.log(msg)
    fun log(msg: String, t: Throwable) = xposedInterface.log(msg, t)
}

/**
 * Registry để map Member -> MethodHookCallback.
 * Thread-safe với ConcurrentHashMap.
 */
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

/**
 * DynamicHooker - R8-compatible Hooker implementation.
 * 
 * CRITICAL: Phải có annotations để R8 không strip:
 * - @XposedHooker trên class
 * - @BeforeInvocation trên before()
 * - @AfterInvocation trên after()
 * - Method names PHẢI là "before" và "after"
 */
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
```

---

## LoadPackageParam.kt

```kotlin
package [package].bridge

import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam as ApiParam

/**
 * Wrapper cho PackageLoadedParam.
 * Tách khỏi API dependency trong business logic.
 */
class LoadPackageParam(private val param: ApiParam) {
    /** Package name của app đang được hook. */
    val packageName: String get() = param.packageName
    
    /** ClassLoader của app. */
    val classLoader: ClassLoader get() = param.classLoader
    
    /** True nếu đây là package đầu tiên trong process. */
    val isFirstPackage: Boolean get() = param.isFirstPackage
    
    /** Access original param nếu cần. */
    val original: ApiParam get() = param
}
```

---

## HookParam.kt

```kotlin
package [package].bridge

import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import java.lang.reflect.Member

/**
 * Unified wrapper cho BeforeHookCallback và AfterHookCallback.
 * Cung cấp Kotlin-friendly properties.
 */
class HookParam private constructor(
    private val beforeCallback: BeforeHookCallback?,
    private val afterCallback: AfterHookCallback?,
    private val isBefore: Boolean
) {
    companion object {
        @JvmStatic
        fun fromBefore(callback: BeforeHookCallback) = HookParam(callback, null, true)
        
        @JvmStatic
        fun fromAfter(callback: AfterHookCallback) = HookParam(null, callback, false)
    }
    
    /** Method/Constructor đang được hook. */
    val method: Member 
        get() = beforeCallback?.member ?: afterCallback!!.member
    
    /** `this` object (null nếu static method). */
    val thisObject: Any? 
        get() = beforeCallback?.thisObject ?: afterCallback?.thisObject
    
    /** Arguments array. MUTABLE - có thể modify trực tiếp. */
    val args: Array<Any?> 
        get() = beforeCallback?.args ?: afterCallback!!.args
    
    /**
     * Return value.
     * - Get: Chỉ có ý nghĩa trong afterHook
     * - Set: Trong beforeHook sẽ skip original method
     */
    var result: Any?
        get() = afterCallback?.result
        set(value) {
            if (isBefore) {
                beforeCallback?.returnAndSkip(value)
            } else {
                afterCallback?.setResult(value)
            }
        }
    
    /**
     * Exception nếu method throw.
     * - Get: Lấy exception
     * - Set: Throw exception thay vì return normally
     */
    var throwable: Throwable?
        get() = afterCallback?.throwable
        set(value) {
            if (isBefore && value != null) {
                beforeCallback?.throwAndSkip(value)
            } else if (!isBefore) {
                afterCallback?.setThrowable(value)
            }
        }
    
    /** True nếu original method bị skip (trong afterHook). */
    val isSkipped: Boolean
        get() = afterCallback?.isSkipped ?: false
}
```

---

## MethodHookCallback.kt

```kotlin
package [package].bridge

/**
 * Abstract callback class cho hooks.
 * Override beforeHook()/afterHook() để xử lý.
 */
abstract class MethodHookCallback {
    /**
     * Called TRƯỚC khi method gốc execute.
     * Có thể gọi param.result = value để skip original.
     */
    open fun beforeHook(param: HookParam) {}
    
    /**
     * Called SAU khi method gốc execute (hoặc skip).
     * Có thể modify result hoặc throw exception.
     */
    open fun afterHook(param: HookParam) {}
}
```

---

## Unhook.kt

```kotlin
package [package].bridge

import io.github.libxposed.api.XposedInterface.MethodUnhooker
import java.lang.reflect.Member

/**
 * Handle để gỡ bỏ hook đã register.
 */
class Unhook<T : Member>(private val inner: MethodUnhooker<T>) {
    /** Method đã được hook. */
    val hookedMethod: T get() = inner.origin
    
    /** Gỡ bỏ hook này. */
    fun unhook() = inner.unhook()
}
```

---

## Sử dụng Bridge Layer

### Từ ModuleMain (Entry)

```kotlin
class ModuleMain(base: XposedInterface, param: ModuleLoadedParam) : XposedModule(base, param) {
    init {
        // Initialize Bridge Layer
        Xposed.init(base)
    }
    
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return
        
        // Use Bridge Layer wrappers
        val lpp = LoadPackageParam(param)
        MyHandler().hook(lpp)
    }
}
```

### Từ HookUtils (Arch Layer)

```kotlin
// Extension functions wrap Bridge methods
inline fun Method.hookAfter(
    crossinline cond: () -> Boolean = { true },
    crossinline fn: (HookParam) -> Unit
): Unhook<Method> = Xposed.hookMethod(this, object : MethodHookCallback() {
    override fun afterHook(param: HookParam) {
        if (cond()) fn(param)
    }
})

inline fun Class<*>.hookAllAfter(
    name: String,
    crossinline cond: () -> Boolean = { true },
    crossinline fn: (HookParam) -> Unit
): Set<Unhook<Method>> = Xposed.hookAllMethods(this, name, object : MethodHookCallback() {
    override fun afterHook(param: HookParam) {
        if (cond()) fn(param)
    }
})
```

---

## ProGuard Rules (Bắt Buộc)

```proguard
# Keep Bridge Layer
-keep class [package].bridge.** { *; }

# Keep DynamicHooker với annotations
-keep @io.github.libxposed.api.annotations.XposedHooker class * {
    @io.github.libxposed.api.annotations.BeforeInvocation public static *;
    @io.github.libxposed.api.annotations.AfterInvocation public static *;
}

# Keep dummy annotations
-keep @interface io.github.libxposed.api.annotations.*
```

---

## So sánh với OLD Xposed API

| Aspect | OLD (de.robv.android.xposed) | NEW (LibXposed API 100) |
|--------|------------------------------|-------------------------|
| Entry | `IXposedHookLoadPackage` | `XposedModule` |
| Callback | `XC_MethodHook` | `Hooker` interface |
| Hook Param | `MethodHookParam` | `BeforeHookCallback`/`AfterHookCallback` |
| Skip Method | `setResult()` | `returnAndSkip()` |
| Annotations | Không cần | `@XposedHooker`, `@BeforeInvocation`, `@AfterInvocation` |
| R8 Compatible | Cần keep rules phức tạp | Cần `libxposed-compat` module |
