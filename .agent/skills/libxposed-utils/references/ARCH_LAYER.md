# Arch Layer - Chi tiết Kỹ thuật (LibXposed API 100)

> Lớp kiến trúc ứng dụng với IHook pattern, HookUtils DSL, ReflectUtil, và DynHook system.
> Tất cả code tuân thủ LibXposed API 100 standards.

## Kiến trúc Tổng quan

```
┌─────────────────────────────────────────────────────────────────┐
│                    Handler Classes                               │
│  TelegramHandler, SystemUIHandler, FeatureManager, ...          │
├─────────────────────────────────────────────────────────────────┤
│                       Arch Layer                                 │
│  IHook │ HookUtils │ ReflectUtil │ DynHook │ ObfsUtils │ Utils │
├─────────────────────────────────────────────────────────────────┤
│                      Bridge Layer                                │
│  Xposed │ LoadPackageParam │ HookParam │ DynamicHooker          │
├─────────────────────────────────────────────────────────────────┤
│                    LibXposed API 100                             │
│  XposedModule │ XposedInterface │ Hooker │ Callbacks            │
└─────────────────────────────────────────────────────────────────┘
```

---

## IHook.kt - Base Hook Handler

```kotlin
package [package].arch

import [package].bridge.LoadPackageParam
import [package].bridge.Xposed

/**
 * Base class cho tất cả hook handlers.
 * Cung cấp:
 * - Access đến LoadPackageParam và ClassLoader
 * - Template method pattern với onHook()
 * - Error handling tự động
 * - Sub-hook composition
 */
abstract class IHook {
    /** ClassLoader của target app. */
    lateinit var classLoader: ClassLoader
        private set
    
    /** LoadPackageParam từ framework. */
    lateinit var loadPackageParam: LoadPackageParam
        private set
    
    /**
     * Entry point - được gọi từ ModuleMain.
     * Wrap onHook() với error handling.
     */
    open fun hook(param: LoadPackageParam, loader: ClassLoader = param.classLoader) {
        loadPackageParam = param
        classLoader = loader
        try {
            onHook()
        } catch (t: Throwable) {
            Xposed.log("Hook failed: ${this.javaClass.simpleName}", t)
        }
    }
    
    /**
     * Gọi sub-hook với cùng context.
     * Cho phép composition các features.
     */
    fun subHook(hook: IHook) {
        hook.hook(loadPackageParam, classLoader)
    }
    
    /** Tìm class trong target app. */
    protected fun findClass(name: String): Class<*> = 
        classLoader.loadClass(name)
    
    /** Tìm class, return null nếu không tìm thấy. */
    protected fun findClassOrNull(name: String): Class<*>? = 
        runCatching { classLoader.loadClass(name) }.getOrNull()
    
    /** Override trong subclass để register hooks. */
    protected abstract fun onHook()
}
```

**Usage Pattern:**

```kotlin
class MyHandler : IHook() {
    override fun onHook() {
        // findClass() available
        // classLoader available
        // loadPackageParam available
        
        findClass("com.target.Class").hookAllAfter("method") { param ->
            logI("Method called!")
        }
        
        // Compose với sub-handlers
        subHook(FeatureAHandler())
        subHook(FeatureBHandler())
    }
}
```

---

## HookUtils.kt - Hooking DSL

```kotlin
@file:Suppress("NOTHING_TO_INLINE")

package [package].arch

import [package].bridge.*
import java.lang.reflect.*

typealias HookCallback = (HookParam) -> Unit
typealias HookReplacement = (HookParam) -> Any?

// ══════════════════════════════════════════════════════════════════
// METHOD EXTENSIONS - Hook specific method instance
// ══════════════════════════════════════════════════════════════════

/**
 * Hook method với before callback.
 */
inline fun Method.hookBefore(
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
): Unhook<Method> = Xposed.hookMethod(this, object : MethodHookCallback() {
    override fun beforeHook(param: HookParam) {
        if (cond()) fn(param)
    }
})

/**
 * Hook method với after callback.
 */
inline fun Method.hookAfter(
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
): Unhook<Method> = Xposed.hookMethod(this, object : MethodHookCallback() {
    override fun afterHook(param: HookParam) {
        if (cond()) fn(param)
    }
})

/**
 * Replace method hoàn toàn.
 * Original method KHÔNG được gọi.
 */
inline fun Method.hookReplace(
    crossinline cond: () -> Boolean = { true },
    crossinline replacement: HookReplacement
): Unhook<Method> = Xposed.hookMethod(this, object : MethodHookCallback() {
    override fun beforeHook(param: HookParam) {
        if (cond()) {
            try {
                param.result = replacement(param)
            } catch (t: Throwable) {
                param.throwable = t
            }
        }
    }
})

/** Return constant thay vì chạy method gốc. */
fun Method.hookConstant(constant: Any?): Unhook<Method> = hookReplace { constant }

/** No-op method (return null). */
fun Method.hookNop(): Unhook<Method> = hookConstant(null)

// ══════════════════════════════════════════════════════════════════
// CLASS.hookAll* - Hook tất cả methods cùng tên
// ══════════════════════════════════════════════════════════════════

/**
 * Hook tất cả overloads của một method với after callback.
 */
inline fun Class<*>.hookAllAfter(
    name: String,
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
): Set<Unhook<Method>> = Xposed.hookAllMethods(this, name, object : MethodHookCallback() {
    override fun afterHook(param: HookParam) {
        if (cond()) fn(param)
    }
})

/**
 * Hook tất cả overloads với before callback.
 */
inline fun Class<*>.hookAllBefore(
    name: String,
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
): Set<Unhook<Method>> = Xposed.hookAllMethods(this, name, object : MethodHookCallback() {
    override fun beforeHook(param: HookParam) {
        if (cond()) fn(param)
    }
})

/**
 * Replace tất cả overloads hoàn toàn.
 */
inline fun Class<*>.hookAllReplace(
    name: String,
    crossinline cond: () -> Boolean = { true },
    crossinline replacement: HookReplacement
): Set<Unhook<Method>> = Xposed.hookAllMethods(this, name, object : MethodHookCallback() {
    override fun beforeHook(param: HookParam) {
        if (cond()) {
            try {
                param.result = replacement(param)
            } catch (t: Throwable) {
                param.throwable = t
            }
        }
    }
})

/** Return constant cho tất cả overloads. */
fun Class<*>.hookAllConstant(name: String, constant: Any?): Set<Unhook<Method>> =
    hookAllReplace(name) { constant }

/** No-op tất cả overloads. */
fun Class<*>.hookAllNop(name: String): Set<Unhook<Method>> = 
    hookAllConstant(name, null)

/** Conditional no-op. */
inline fun Class<*>.hookAllNopIf(
    name: String, 
    crossinline cond: () -> Boolean
): Set<Unhook<Method>> = hookAllBefore(name) { param ->
    if (cond()) param.result = null
}

// ══════════════════════════════════════════════════════════════════
// CLASS.hookAllC* - Hook tất cả constructors
// ══════════════════════════════════════════════════════════════════

/**
 * Hook tất cả constructors với after callback.
 */
inline fun Class<*>.hookAllCAfter(
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
): Set<Unhook<Constructor<*>>> = Xposed.hookAllConstructors(this, object : MethodHookCallback() {
    override fun afterHook(param: HookParam) {
        if (cond()) fn(param)
    }
})

/**
 * Hook tất cả constructors với before callback.
 */
inline fun Class<*>.hookAllCBefore(
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
): Set<Unhook<Constructor<*>>> = Xposed.hookAllConstructors(this, object : MethodHookCallback() {
    override fun beforeHook(param: HookParam) {
        if (cond()) fn(param)
    }
})

// ══════════════════════════════════════════════════════════════════
// CLASS.hook* - Find và hook method exact signature
// ══════════════════════════════════════════════════════════════════

/**
 * Tìm method exact signature rồi hook với after callback.
 */
inline fun Class<*>.hookAfter(
    name: String,
    vararg types: Class<*>,
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
): Unhook<Method> = Xposed.findMethodExact(this, name, *types).hookAfter(cond, fn)

/**
 * Tìm method exact signature rồi hook với before callback.
 */
inline fun Class<*>.hookBefore(
    name: String,
    vararg types: Class<*>,
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
): Unhook<Method> = Xposed.findMethodExact(this, name, *types).hookBefore(cond, fn)

/**
 * Tìm method exact signature rồi replace.
 */
inline fun Class<*>.hookReplace(
    name: String,
    vararg types: Class<*>,
    crossinline cond: () -> Boolean = { true },
    crossinline replacement: HookReplacement
): Unhook<Method> = Xposed.findMethodExact(this, name, *types).hookReplace(cond, replacement)

fun Class<*>.hookConstant(name: String, vararg types: Class<*>, constant: Any?): Unhook<Method> =
    Xposed.findMethodExact(this, name, *types).hookConstant(constant)

fun Class<*>.hookNop(name: String, vararg types: Class<*>): Unhook<Method> =
    Xposed.findMethodExact(this, name, *types).hookNop()

// ══════════════════════════════════════════════════════════════════
// CLASS.hookC* - Find và hook constructor exact signature  
// ══════════════════════════════════════════════════════════════════

inline fun Class<*>.hookCAfter(
    vararg types: Class<*>,
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
): Unhook<Constructor<*>> {
    val constructor = Xposed.findConstructorExact(this, *types)
    return Xposed.hookConstructor(constructor, object : MethodHookCallback() {
        override fun afterHook(param: HookParam) {
            if (cond()) fn(param)
        }
    })
}

inline fun Class<*>.hookCBefore(
    vararg types: Class<*>,
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
): Unhook<Constructor<*>> {
    val constructor = Xposed.findConstructorExact(this, *types)
    return Xposed.hookConstructor(constructor, object : MethodHookCallback() {
        override fun beforeHook(param: HookParam) {
            if (cond()) fn(param)
        }
    })
}

// ══════════════════════════════════════════════════════════════════
// CLASSLOADER Extensions
// ══════════════════════════════════════════════════════════════════

fun ClassLoader.findClassN(name: String): Class<*>? = 
    runCatching { loadClass(name) }.getOrNull()

fun ClassLoader.findClass(name: String): Class<*> = loadClass(name)

/** Tìm một trong nhiều class names (cho obfuscated code). */
fun ClassLoader.findClassOf(vararg names: String): Class<*> {
    for (name in names) {
        findClassN(name)?.let { return it }
    }
    error("None of classes found: ${names.joinToString(",")}")
}
```

---

## ReflectUtil.kt - Reflection Extensions

```kotlin
@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")

package [package].arch

import [package].bridge.Xposed
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
```

---

## DynHook.kt - Dynamic Hook System

```kotlin
package [package].arch

import [package].bridge.LoadPackageParam
import [package].bridge.Xposed
import java.io.*

/**
 * Manager cho dynamic hooks với settings persistence.
 * @param T Settings data class type
 */
abstract class DynHookManager<T : Any> : IHook() {
    private val hooks = mutableListOf<DynHook>()
    
    /** Current settings. */
    lateinit var settings: T
        protected set
    
    /** File lưu settings. */
    lateinit var settingFile: File
        protected set
    
    /** Override để enable/disable toàn bộ manager. */
    open fun isEnabled(): Boolean = true
    
    /** Khi settings thay đổi - rehook tất cả DynHooks. */
    fun onChanged() {
        hooks.forEach { subHook(it as IHook) }
    }
    
    /** Đăng ký dynamic hook. */
    fun subHook(hook: DynHook) {
        hooks.add(hook)
        hook.parent = this
        super.subHook(hook as IHook)
    }
    
    override fun onHook() {
        settingFile = File("/data/data/${loadPackageParam.packageName}/my_module_settings")
        readSettings()
    }
    
    private fun readSettings() {
        settings = runCatching {
            if (settingFile.canRead()) {
                settingFile.inputStream().use { onReadSettings(it) }
            } else {
                defaultSettings()
            }
        }.onFailure {
            Xposed.log("Read settings failed", it)
            settingFile.delete()
        }.getOrDefault(defaultSettings())
        
        Xposed.log("Current settings: $settings")
    }
    
    /** Update settings và persist. */
    fun updateSettings(newSettings: T) {
        settings = newSettings
        onChanged()
        runCatching {
            settingFile.outputStream().use { onWriteSettings(it, newSettings) }
        }.onFailure { Xposed.log("Persist settings failed", it) }
    }
    
    /** Deserialize settings. */
    abstract fun onReadSettings(input: InputStream): T
    
    /** Default settings khi chưa có file. */
    abstract fun defaultSettings(): T
    
    /** Serialize settings. */
    abstract fun onWriteSettings(output: OutputStream, setting: T)
}

/**
 * Dynamic hook - chỉ hook khi feature enabled.
 * Hook được đánh dấu đã apply và không hook lại.
 */
abstract class DynHook : IHook() {
    private var hooked = false
    lateinit var parent: DynHookManager<*>
    
    @Synchronized
    override fun hook(param: LoadPackageParam, loader: ClassLoader) {
        if (hooked) return
        if (!isEnabled()) return
        super.hook(param, loader)
        hooked = true
    }
    
    /** Check feature-specific condition. */
    abstract fun isFeatureEnabled(): Boolean
    
    /** Combined enabled check. */
    protected open fun isEnabled(): Boolean = parent.isEnabled() && isFeatureEnabled()
}
```

---

## ObfsUtils.kt - DexKit Integration

```kotlin
package [package].arch

import [package].bridge.Xposed
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.*
import java.io.File

val _loadDexKit by lazy { System.loadLibrary("dexkit") }

typealias ObfsTable = Map<String, ObfsInfo>
typealias MutableObfsTable = MutableMap<String, ObfsInfo>

data class ObfsInfo(
    val className: String,
    val memberName: String
)

fun MethodData.toObfsInfo() = ObfsInfo(className = className, memberName = methodName)
fun FieldData.toObfsInfo() = ObfsInfo(className = className, memberName = fieldName)

// Cache keys
const val OBFS_KEY_APK = "apkPath"
const val OBFS_KEY_TABLE_VERSION = "tableVersion"
const val OBFS_KEY_FRAMEWORK_VERSION = "frameworkVersion"
const val OBFS_KEY_PREFIX_METHOD = "method_"
const val OBFS_FRAMEWORK_VERSION = 1

/**
 * Tạo hoặc load cached obfuscation table.
 * 
 * @param name Unique name cho cache file
 * @param tableVersion Increment khi thay đổi DexKit queries
 * @param force Force regenerate cache
 * @param creator DexKit query logic
 */
fun IHook.createObfsTable(
    name: String,
    tableVersion: Int,
    force: Boolean = false,
    creator: (DexKitBridge) -> ObfsTable
): ObfsTable {
    val packageName = loadPackageParam.packageName
    val apkPath = "/data/app/$packageName/base.apk" // Simplified path
    
    val tableFile = File("/data/data/$packageName/cache/obfs_table_$name.json")
    tableFile.parentFile?.mkdirs()
    
    // Try load from cache
    runCatching {
        if (!force && tableFile.isFile) JSONObject(tableFile.readText()) else null
    }.onFailure { Xposed.log("Read obfs-table $tableFile", it) }
     .getOrNull()?.let { json ->
        runCatching {
            // Validate cache
            if (json.getInt(OBFS_KEY_FRAMEWORK_VERSION) != OBFS_FRAMEWORK_VERSION) return@runCatching
            if (json.getString(OBFS_KEY_APK) != apkPath) return@runCatching
            if (json.getInt(OBFS_KEY_TABLE_VERSION) != tableVersion) return@runCatching
            
            // Parse cached table
            val outMap = mutableMapOf<String, ObfsInfo>()
            for (k in json.keys()) {
                if (k.startsWith("method")) {
                    val rk = k.removePrefix(OBFS_KEY_PREFIX_METHOD)
                    val rv = json[k] as JSONObject
                    outMap[rk] = ObfsInfo(
                        className = rv.getString("className"),
                        memberName = rv.getString("methodName")
                    )
                }
            }
            Xposed.log("Use cached obfs-table $name")
            return outMap
        }.onFailure { Xposed.log("Parsing obfs-table", it) }
    }
    
    // Create new table with DexKit
    _loadDexKit
    val dexKitBridge = DexKitBridge.create(apkPath)
    
    return creator(dexKitBridge).also { table ->
        // Save to cache
        runCatching {
            val outJson = JSONObject().apply {
                put(OBFS_KEY_APK, apkPath)
                put(OBFS_KEY_TABLE_VERSION, tableVersion)
                put(OBFS_KEY_FRAMEWORK_VERSION, OBFS_FRAMEWORK_VERSION)
                for ((k, v) in table) {
                    put(OBFS_KEY_PREFIX_METHOD + k, JSONObject().apply {
                        put("className", v.className)
                        put("methodName", v.memberName)
                    })
                }
            }
            tableFile.delete()
            tableFile.writeText(outJson.toString())
            Xposed.log("Created obfs-table $name")
        }.onFailure { Xposed.log("Save obfs-table", it) }
    }
}
```

---

## ExtraField.kt - Property Delegate

```kotlin
package [package].arch

import kotlin.reflect.KProperty
import java.util.WeakHashMap

/**
 * Property delegate để attach data vào object instances.
 * Sử dụng WeakHashMap để tránh memory leaks.
 */
private val extraFields = WeakHashMap<Any, MutableMap<String, Any?>>()

class ExtraField<T>(
    private val bound: Any,
    private val name: String, 
    private val defValue: T
) {
    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return extraFields[bound]?.get(name) as T? ?: defValue
    }
    
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        extraFields.getOrPut(bound) { mutableMapOf() }[name] = value
    }
}

/** Factory function. */
fun <T> extraField(bound: Any, name: String, def: T): ExtraField<T> = 
    ExtraField(bound, name, def)
```

**Usage:**

```kotlin
findClass("com.example.User").hookAllCAfter { param ->
    val user = param.thisObject!!
    
    // Attach custom data
    var trackedAt by extraField(user, "module_trackedAt", 0L)
    trackedAt = System.currentTimeMillis()
    
    // Read later
    logI("User tracked at: $trackedAt")
}
```

---

## Utils.kt - Android Utilities

```kotlin
package [package].arch

import android.app.Activity
import android.content.*
import android.content.res.*
import android.os.*
import android.util.TypedValue
import android.view.*

// ══════════════════════════════════════════════════════════════════
// View Utilities
// ══════════════════════════════════════════════════════════════════

/** Tìm view đệ quy theo predicate. */
fun View.findView(predicate: (View) -> Boolean): View? {
    if (predicate(this)) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).findView(predicate)?.let { return it }
        }
    }
    return null
}

// ══════════════════════════════════════════════════════════════════
// Dimension Conversion
// ══════════════════════════════════════════════════════════════════

fun Float.dp2px(resources: Resources) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)

fun Float.sp2px(resources: Resources) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this, resources.displayMetrics)

fun Int.dp2px(resources: Resources) = toFloat().dp2px(resources)
fun Int.sp2px(resources: Resources) = toFloat().sp2px(resources)

// ══════════════════════════════════════════════════════════════════
// Intent Utilities
// ══════════════════════════════════════════════════════════════════

@Suppress("deprecation")
fun <T : Parcelable> Intent.getParcelableExtraCompat(key: String, clz: Class<T>): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, clz)
    } else {
        getParcelableExtra(key)
    }

// ══════════════════════════════════════════════════════════════════
// Activity Utilities
// ══════════════════════════════════════════════════════════════════

fun restartApplication(activity: Activity) {
    val pm = activity.packageManager
    val intent = pm.getLaunchIntentForPackage(activity.packageName)
    activity.finishAffinity()
    activity.startActivity(intent)
    kotlin.system.exitProcess(0)
}
```

---

## HiddenApiBypassExt.kt - Hidden API Utilities

```kotlin
@file:Suppress("NOTHING_TO_INLINE")

package [package].arch

import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Field
import java.lang.reflect.Method

// ══════════════════════════════════════════════════════════════════
// Initialize - Gọi một lần khi module load
// ══════════════════════════════════════════════════════════════════

/**
 * Enable hidden API bypass cho tất cả classes.
 * Thường gọi trong XposedModule constructor hoặc đầu onHook().
 * 
 * @return true nếu bypass thành công hoặc không cần bypass (Android < P)
 */
inline fun enableHiddenApiBypass(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        HiddenApiBypass.addHiddenApiExemptions("")
    } else {
        true  // Không cần bypass trên Android O trở xuống
    }
}

// ══════════════════════════════════════════════════════════════════
// Method Access
// ══════════════════════════════════════════════════════════════════

/**
 * Lấy hidden method. Dùng khi getDeclaredMethod bị blocked.
 * 
 * @param name Method name
 * @param paramTypes Parameter types
 * @return Method object (accessible)
 * @throws NoSuchMethodException nếu không tìm thấy
 */
inline fun Class<*>.getHiddenMethod(name: String, vararg paramTypes: Class<*>): Method =
    HiddenApiBypass.getDeclaredMethod(this, name, *paramTypes)

/**
 * Gọi hidden method trực tiếp.
 * 
 * @param thisObj this object (null cho static method)
 * @param name Method name
 * @param args Arguments
 * @return Return value cast to T
 */
@Suppress("UNCHECKED_CAST")
inline fun <T> Class<*>.invokeHidden(thisObj: Any?, name: String, vararg args: Any?): T =
    HiddenApiBypass.invoke(this, thisObj, name, *args) as T

// ══════════════════════════════════════════════════════════════════
// Field Access
// ══════════════════════════════════════════════════════════════════

/**
 * Lấy hidden instance field.
 * 
 * @param name Field name
 * @return Field object (accessible) hoặc null nếu không tìm thấy
 */
inline fun Class<*>.getHiddenField(name: String): Field? =
    HiddenApiBypass.getInstanceFields(this).find { it.name == name }?.apply {
        isAccessible = true
    }

/**
 * Lấy hidden static field.
 * 
 * @param name Field name
 * @return Field object (accessible) hoặc null nếu không tìm thấy
 */
inline fun Class<*>.getHiddenStaticField(name: String): Field? =
    HiddenApiBypass.getStaticFields(this).find { it.name == name }?.apply {
        isAccessible = true
    }

// ══════════════════════════════════════════════════════════════════
// Instance Creation
// ══════════════════════════════════════════════════════════════════

/**
 * Tạo instance với hidden constructor.
 * 
 * @param args Constructor arguments
 * @return New instance cast to T
 */
@Suppress("UNCHECKED_CAST")
inline fun <T> Class<*>.newHiddenInstance(vararg args: Any?): T =
    HiddenApiBypass.newInstance(this, *args) as T
```

**Usage Example:**

```kotlin
class SystemHandler : IHook() {
    override fun onHook() {
        // Enable bypass first - chỉ cần gọi một lần
        enableHiddenApiBypass()
        
        // Access hidden ActivityThread
        findClass("android.app.ActivityThread").let { clazz ->
            // Lấy và gọi hidden static method
            val currentMethod = clazz.getHiddenMethod("currentActivityThread")
            val thread = currentMethod.invoke(null)
            
            // Access hidden field
            clazz.getHiddenField("mBoundApplication")?.let { field ->
                val boundApp = field.get(thread)
                logI("BoundApplication: $boundApp")
            }
        }
        
        // Gọi hidden method trực tiếp
        val uses = findClass("android.app.ApplicationInfo")
            .invokeHidden<Boolean>(appInfo, "usesNonSdkApi")
    }
}
```

---

## ProGuard Rules (Bắt Buộc)

```proguard
# Keep IHook implementations
-keep class * extends [package].arch.IHook { *; }

# Keep DynHook implementations  
-keep class * extends [package].arch.DynHook { *; }

# Keep bridge classes
-keep class [package].bridge.** { *; }

# Keep dummy annotations for R8 compatibility
-keep @interface io.github.libxposed.api.annotations.*
-keep @io.github.libxposed.api.annotations.XposedHooker class * {
    @io.github.libxposed.api.annotations.BeforeInvocation public static *;
    @io.github.libxposed.api.annotations.AfterInvocation public static *;
}

# Keep HiddenApiBypass (BẮT BUỘC)
-keep class org.lsposed.hiddenapibypass.** { *; }
-keep class stub.** { *; }
```
