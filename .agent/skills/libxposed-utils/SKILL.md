---
name: libxposed-utils
description: Thư viện tiện ích cho LibXposed API 100. Bao gồm Bridge Layer (wrapper Xposed API), Arch Layer (IHook pattern, HookUtils DSL, ReflectUtil, DynHook), IPC mechanisms (BroadcastManager, ResultReceiver), và Hot Loading. Sử dụng khi cần simplified hooking API, reflection helpers, dynamic settings, hoặc system_server communication.
metadata:
  author: libxposed-community
  version: "1.0"
  based_on: MyInjector
---

# LibXposed Utils Skill

> **Self-Contained Knowledge Base** cho LibXposed 100 Utility Library - Bridge + Arch layers lấy cảm hứng từ MyInjector.
> Skill này PHẢI được sử dụng cùng với `libxposed-api` skill.

## Quick Reference

| Layer | Component | Mục đích |
|-------|-----------|----------|
| **Bridge** | `Xposed` | Wrapper XposedInterface, DynamicHooker với annotations |
| **Bridge** | `LoadPackageParam` | Wrap PackageLoadedParam |
| **Bridge** | `HookParam` | Wrap BeforeHookCallback/AfterHookCallback với getter/setter |
| **Bridge** | `MethodHookCallback` | Abstract callback với `beforeHook()`/`afterHook()` |
| **Bridge** | `Unhook` | Handle để gỡ bỏ hook |
| **Bridge** | `HotLoadHook` | Interface cho hot-reloadable hooks |
| **Arch** | `IHook` | Base class cho tất cả hook handlers |
| **Arch** | `HookUtils` | DSL extensions cho hooking (hookAfter, hookReplace, etc.) |
| **Arch** | `ReflectUtil` | Extension functions cho reflection |
| **Arch** | `DynHook/DynHookManager` | Dynamic hooks với settings persistence |
| **Arch** | `ObfsUtils` | DexKit integration với caching |
| **Arch** | `ExtraField` | Property delegate để attach data vào objects |
| **Arch** | `HiddenApiBypassExt` | Bypass Android hidden API restrictions (API 28+) |
| **IPC** | `BroadcastManager` | System_server broadcast receiver |
| **IPC** | `ResultReceiver` | Binder-based IPC với callback |

### Dependencies

```kotlin
dependencies {
    compileOnly(libs.libxposed.api)
    compileOnly(project(":libxposed-compat"))  // R8 compat annotations - BẮT BUỘC
    implementation(libs.libxposed.service)
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")  // Hidden API bypass - BẮT BUỘC
    implementation("org.luckypray:dexkit:2.0.0")  // Optional, cho ObfsUtils
}
```

### R8 Compatibility (Bắt Buộc)

| Yêu cầu | Mô tả |
|---------|-------|
| **Method naming** | Tên PHẢI là `before` và `after` |
| **Annotations** | `@XposedHooker` trên DynamicHooker class, `@BeforeInvocation`/`@AfterInvocation` trên methods |
| **Dependency** | `compileOnly(project(":libxposed-compat"))` |

---

## Examples

### Basic Handler với IHook

```kotlin
class TelegramHandler : IHook() {
    override fun onHook() {
        findClass("org.telegram.ui.LaunchActivity").hookAllCAfter { param ->
            logI("LaunchActivity created")
        }
        
        findClass("org.telegram.messenger.MessagesController")
            .hookAfter("sendMessage", String::class.java, Long::class.java) { param ->
                val message = param.args[0] as String
                logI("Sending message: $message")
            }
    }
}
```

### Conditional Hooking với DynHook

```kotlin
data class Settings(
    val blockAds: Boolean = false,
    val debugMode: Boolean = false
)

object FeatureManager : DynHookManager<Settings>() {
    override fun defaultSettings() = Settings()
    
    override fun onReadSettings(input: InputStream) = 
        Json.decodeFromStream<Settings>(input)
    
    override fun onWriteSettings(output: OutputStream, setting: Settings) = 
        Json.encodeToStream(setting, output)
    
    override fun onHook() {
        super.onHook()  // Load settings
        subHook(AdBlockHook)
    }
}

object AdBlockHook : DynHook() {
    override fun isFeatureEnabled() = (parent as FeatureManager).settings.blockAds
    
    override fun onHook() {
        findClass("com.example.AdLoader").hookAllNop("showAd")
    }
}
```

### Reflection Helpers

```kotlin
findClass("com.example.Config").hookAllCAfter { param ->
    val config = param.thisObject
    
    // Read private fields
    val apiKey: String = config.getObjAs("mApiKey")
    val endpoint: String? = config.getObjAsN("mEndpoint")
    
    // Write fields
    config.setObj("mDebugMode", true)
    
    // Call private methods
    config.call("initialize", "custom_value")
    
    // Static access
    val instance = findClass("com.example.Singleton").getObjSAs<Any>("INSTANCE")
}
```

### DexKit với Caching

```kotlin
override fun onHook() {
    val obfsTable = createObfsTable("feature_hooks", tableVersion = 3) { dexKit ->
        buildMap {
            dexKit.findMethod {
                matcher {
                    usingStrings("SomeUniqueString")
                    returnType = "void"
                }
            }.firstOrNull()?.let { put("targetMethod", it.toObfsInfo()) }
        }
    }
    
    obfsTable["targetMethod"]?.let { info ->
        findClass(info.className).hookAllAfter(info.memberName) { param ->
            logI("Hooked obfuscated method!")
        }
    }
}
```

---

## Patterns

### Pattern 1: Safe Hook với Error Handling

```kotlin
override fun onHook() {
    runCatching {
        findClass("com.target.TargetClass")
            .hookAfter("targetMethod", String::class.java) { param ->
                // Hook logic
            }
    }.onFailure { 
        logE("Hook failed", it) 
    }
}
```

### Pattern 2: Replace Method Return

```kotlin
findClass("com.example.Checker").hookAllReplace("isValid") { param ->
    true  // Always return true, skip original
}
```

### Pattern 3: Modify Arguments

```kotlin
findClass("com.example.Api").hookAllBefore("request") { param ->
    val args = param.args
    args[0] = "modified_url"  // Arrays are mutable
    args[1] = mapOf("injected" to "header")
}
```

### Pattern 4: Call Original Method

```kotlin
findClass("com.example.Wrapper").hookAllReplace("process") { param ->
    val originalResult = param.method.callOrig(param.thisObject, *param.args)
    "Wrapped: $originalResult"
}
```

### Pattern 5: Attach Data to Objects

```kotlin
findClass("com.example.Session").hookAllCAfter { param ->
    val session = param.thisObject
    var customData by extraField(session, "myModule_data", mutableMapOf<String, Any>())
    customData["createdAt"] = System.currentTimeMillis()
}
```

### Pattern 6: Sub-hooks Composition

```kotlin
class MainHandler : IHook() {
    override fun onHook() {
        subHook(FeatureAHandler())
        subHook(FeatureBHandler())
        subHook(FeatureCHandler())
    }
}
```

### Pattern 7: Conditional Hook Activation

```kotlin
findClass("com.example.Feature").hookAllNopIf("showPopup") {
    prefs.getBoolean("block_popups", false)
}
```

### Pattern 8: DynamicHooker với R8 Annotations (Bắt Buộc)

```kotlin
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.annotations.XposedHooker
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.AfterInvocation

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

### Pattern 9: Bypass Hidden API với HiddenApiBypass (Bắt Buộc cho Hidden API)

```kotlin
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Khởi tạo Hidden API bypass khi module load.
 * Gọi trong constructor của XposedModule hoặc đầu onHook().
 */
fun enableHiddenApiBypass(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        HiddenApiBypass.addHiddenApiExemptions("")  // Exempt tất cả
    } else {
        true  // Không cần bypass trên Android O trở xuống
    }
}

// Truy cập hidden method
class SystemHandler : IHook() {
    override fun onHook() {
        enableHiddenApiBypass()
        
        // Lấy hidden method
        val atClass = findClass("android.app.ActivityThread")
        val method = HiddenApiBypass.getDeclaredMethod(
            atClass, "currentActivityThread"
        )
        val thread = method.invoke(null)
        
        // Lấy hidden field
        HiddenApiBypass.getInstanceFields(atClass)
            .find { it.name == "mBoundApplication" }?.let { field ->
                field.isAccessible = true
                val boundApp = field.get(thread)
            }
        
        // Gọi hidden method trực tiếp
        val result = HiddenApiBypass.invoke(
            Class.forName("android.app.ApplicationInfo"),
            appInfo,
            "usesNonSdkApi"
        )
    }
}
```

---

## AI Workflow

### Khi Tạo Module Mới với Utils

1. **Tham khảo `libxposed-api` skill** để tạo `libxposed-compat` module
2. **Tạo bridge/ package** với các wrapper classes (copy từ references)
3. **Thêm annotations** vào `DynamicHooker` class (xem Pattern 8)
4. **Tạo arch/ package** với IHook, HookUtils, ReflectUtil
5. **Tạo Handler class** extends `IHook`
6. **Override `onHook()`** để register hooks
7. **Wire vào Entry.kt** trong `onPackageLoaded()`

### Khi Cần Dynamic Settings

1. **Tạo Settings data class** với các fields cần thiết
2. **Tạo Manager object** extends `DynHookManager<Settings>`
3. **Tạo Feature hooks** extends `DynHook` với `isFeatureEnabled()`
4. **Gọi `subHook()`** trong Manager's `onHook()`

### Khi Hook Obfuscated Apps

1. **Sử dụng `createObfsTable()`** với DexKit queries
2. **Increment `tableVersion`** khi thay đổi queries
3. **Check nullable** trước khi sử dụng obfs info
4. **Test với `force = true`** để regenerate cache

### Khi Cần IPC với System Server

1. **Tạo BroadcastManager** với unique action name
2. **Tạo ResultReceiver** cho async response
3. **Verify caller** với `isCallerTrusted()`
4. **Handle reload/unload** lifecycle

---

## API Summary

### Bridge Layer

| Class | Key Methods |
|-------|-------------|
| `Xposed` | `hookMethod()`, `hookAllMethods()`, `hookConstructor()`, `findMethodExact()`, `invokeOriginal()`, `log()` |
| `LoadPackageParam` | `packageName`, `classLoader`, `isFirstPackage`, `original` |
| `HookParam` | `args`, `thisObject`, `result`, `throwable`, `method`, `isSkipped` |
| `MethodHookCallback` | `beforeHook()`, `afterHook()` |
| `DynamicHooker` | `before()`, `after()` - với R8 annotations |

### Arch Layer - HookUtils DSL

| Extension | Signature | Description |
|-----------|-----------|-------------|
| `hookAll` | `Class.hookAll(name, cond, before, after)` | Hook tất cả overloads |
| `hookAllAfter` | `Class.hookAllAfter(name, cond, fn)` | Chỉ after callback |
| `hookAllBefore` | `Class.hookAllBefore(name, cond, fn)` | Chỉ before callback |
| `hookAllReplace` | `Class.hookAllReplace(name, cond, fn)` | Replace hoàn toàn |
| `hookAllNop` | `Class.hookAllNop(name)` | No-op (return null) |
| `hookAfter` | `Class.hookAfter(name, *types, fn)` | Hook method exact signature |
| `hookC` | `Class.hookC(*types, before, after)` | Hook constructor |
| `hook` | `Method.hook(cond, before, after)` | Hook specific method |

### Arch Layer - ReflectUtil

| Extension | Return | Description |
|-----------|--------|-------------|
| `Any?.getObj(name)` | `Any?` | Get field value |
| `Any?.getObjAs<T>(name)` | `T` | Get and cast |
| `Any?.setObj(name, value)` | `Unit` | Set field value |
| `Any?.call(name, *args)` | `Any?` | Call method |
| `Class.getObjS(name)` | `Any?` | Get static field |
| `Class.callS(name, *args)` | `Any?` | Call static method |
| `Class.newInst(*args)` | `Any` | Create instance |
| `ClassLoader.findClass(name)` | `Class<*>` | Find class |
| `ClassLoader.findClassOf(*names)` | `Class<*>` | Find one of classes |
| `Method.deoptimize()` | `Unit` | Force interpreter |
| `Member.callOrig(receiver, *args)` | `Any?` | Call original in hook |

### Arch Layer - HiddenApiBypassExt (Android P+)

| Method | Signature | Description |
|--------|-----------|-------------|
| `enableHiddenApiBypass()` | `fun enableHiddenApiBypass(): Boolean` | Bypass tất cả hidden API restrictions |
| `getHiddenMethod` | `Class.getHiddenMethod(name, *types)` | Lấy hidden method |
| `invokeHidden` | `Class.invokeHidden<T>(thisObj, name, *args)` | Gọi hidden method |
| `getHiddenField` | `Class.getHiddenField(name)` | Lấy hidden instance field |
| `getHiddenStaticField` | `Class.getHiddenStaticField(name)` | Lấy hidden static field |
| `newHiddenInstance` | `Class.newHiddenInstance<T>(*args)` | Tạo instance với hidden constructor |

> **Lưu ý**: Gọi `enableHiddenApiBypass()` một lần trong `onHook()` trước khi sử dụng các hidden API functions.

---

## Constants & Types

```kotlin
typealias HookCallback = (HookParam) -> Unit
typealias HookReplacement = (HookParam) -> Any?
typealias ObfsTable = Map<String, ObfsInfo>

data class ObfsInfo(
    val className: String,
    val memberName: String
)
```

---

## ProGuard Rules

```proguard
# Keep IHook implementations
-keep class * extends [package].arch.IHook { *; }

# Keep DynHook implementations  
-keep class * extends [package].arch.DynHook { *; }

# Keep bridge classes
-keep class [package].bridge.** { *; }

# Keep for hot loading
-keep class [package].bridge.HotLoadHook { *; }

# Keep dummy annotations for R8 compatibility (BẮT BUỘC)
-keep @interface io.github.libxposed.api.annotations.*
-keep @io.github.libxposed.api.annotations.XposedHooker class * {
    @io.github.libxposed.api.annotations.BeforeInvocation public static *;
    @io.github.libxposed.api.annotations.AfterInvocation public static *;
}

# Keep HiddenApiBypass (BẮT BUỘC)
-keep class org.lsposed.hiddenapibypass.** { *; }
-keep class stub.** { *; }
```

---

## See Also

- **@[../libxposed-api/SKILL.md]** - Base API skill (BẮT BUỘC đọc trước)
  - `libxposed-compat` #L201-246 - Dummy annotations module

- @[references/BRIDGE_LAYER.md] (432 lines)
  - `Xposed.kt` #L30-144 - Core wrapper, CallbackRegistry
  - `DynamicHooker` #L164-200 - R8-compatible hooker
  - `LoadPackageParam.kt` #L205-229 - Param wrapper
  - `HookParam.kt` #L231-305 - Unified callback wrapper
  - `MethodHookCallback.kt` #L307-331 - Abstract callback
  - `Unhook.kt` #L333-353 - Unhook handle
  - `ProGuard Rules` #L404-418 - Keep rules

- @[references/ARCH_LAYER.md] (960 lines)
  - `IHook.kt` #L26-84 - Base handler class
  - `HookUtils.kt` #L108-353 - DSL extensions
  - `ReflectUtil.kt` #L357-476 - Reflection helpers
  - `DynHook.kt` #L480-580 - Dynamic hook system
  - `ObfsUtils.kt` #L584-687 - DexKit integration
  - `ExtraField.kt` #L691-723 - Property delegate
  - `HiddenApiBypassExt.kt` #L808-938 - Hidden API helpers
  - `ProGuard Rules` #L940-960 - Keep rules

- @[references/IPC_RUNTIME.md] (663 lines)
  - `ModuleMain.kt` #L35-98 - Entry point lifecycle
  - `XposedService Usage` #L133-198 - IPC trong Activity
  - `Remote Preferences` #L237-285 - Settings sync
  - `Scope Management` #L286-339 - scope.list, requestScope
  - `BroadcastManager.kt` #L341-450 - System server IPC
  - `ResultReceiver.kt` #L452-520 - Binder-based callback
  - `Hot Loading` #L522-620 - Runtime reload
