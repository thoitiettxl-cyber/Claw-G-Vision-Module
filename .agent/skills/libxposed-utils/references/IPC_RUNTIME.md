# IPC và Runtime Mechanisms - Chi tiết Kỹ thuật (LibXposed API 100)

> Giao tiếp liên tiến trình, Hot Loading, và tương tác hệ thống.
> Tất cả code tuân thủ LibXposed API 100 standards.

## Tổng quan Kiến trúc

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              SYSTEM                                          │
├──────────────────────┬────────────────────────┬─────────────────────────────┤
│    MODULE APP        │    SYSTEM_SERVER       │    TARGET APP               │
│                      │                        │                             │
│  ┌────────────────┐  │  ┌──────────────────┐  │  ┌───────────────────────┐  │
│  │ MainActivity   │  │  │ SystemServer     │  │  │ IHook Handlers        │  │
│  │                │  │  │ HookLoader       │  │  │                       │  │
│  │ XposedService  │◄─┼──│ BroadcastManager │  │  │ DynHookManager        │  │
│  │   (IPC)        │  │  │                  │  │  │   └── DynHook         │  │
│  └────────────────┘  │  │ HotLoadHook      │  │  │                       │  │
│         │            │  │   (reloadable)   │  │  │ Settings Persistence  │  │
│         ▼            │  └──────────────────┘  │  └───────────────────────┘  │
│  ┌────────────────┐  │          ▲             │                             │
│  │ XposedService  │──┼──────────┘             │                             │
│  │ Helper         │  │                        │                             │
│  └────────────────┘  │                        │                             │
└──────────────────────┴────────────────────────┴─────────────────────────────┘
```

---

## 1. Entry Point & Module Lifecycle (LibXposed API 100)

### ModuleMain.kt

```kotlin
package [package]

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerLoadedParam
import [package].bridge.LoadPackageParam
import [package].bridge.Xposed
import [package].handlers.*

/**
 * Module Entry - LibXposed API 100
 * Extends XposedModule thay vì implement IXposedHookLoadPackage
 */
class ModuleMain(base: XposedInterface, param: ModuleLoadedParam) : XposedModule(base, param) {
    companion object {
        /** Module path từ ModuleLoadedParam. */
        lateinit var modulePath: String
            private set
        
        /** Process name hiện tại. */
        lateinit var processName: String
            private set
    }
    
    init {
        // Initialize Bridge Layer
        Xposed.init(base)
        
        // Store module info
        modulePath = param.modulePath
        processName = param.processName
        
        log("Module loaded: $processName")
    }
    
    /**
     * Called cho target apps được khai báo trong scope.list.
     */
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return
        
        log("Loading package: ${param.packageName}")
        
        val lpp = LoadPackageParam(param)
        val handler = when (param.packageName) {
            "org.telegram.messenger" -> TelegramHandler()
            "com.android.systemui" -> SystemUIHandler()
            "com.example.target" -> TargetHandler()
            else -> return
        }
        
        handler.hook(lpp)
    }
    
    /**
     * Called khi hook system_server (nếu khai báo trong scope.list).
     */
    override fun onSystemServerLoaded(param: SystemServerLoadedParam) {
        log("Loading system_server")
        SystemServerHookLoader.hook(param)
    }
}
```

### Lifecycle Diagram

```
┌───────────────────────────────────────────────────────────────────┐
│                        LSPOSED FRAMEWORK                           │
│                                                                    │
│   ┌─────────────────────────────────────────────────────────────┐ │
│   │ 1. Load java_init.list                                       │ │
│   │    → ModuleMain class                                        │ │
│   └──────────────────────────┬──────────────────────────────────┘ │
│                              │                                     │
│                              ▼                                     │
│   ┌─────────────────────────────────────────────────────────────┐ │
│   │ 2. Create ModuleMain(XposedInterface, ModuleLoadedParam)    │ │
│   │    → constructor runs                                        │ │
│   │    → Xposed.init(base)                                       │ │
│   └──────────────────────────┬──────────────────────────────────┘ │
│                              │                                     │
│               ┌──────────────┴───────────────┐                    │
│               │                              │                    │
│               ▼                              ▼                    │
│   ┌─────────────────────┐        ┌─────────────────────────────┐ │
│   │ onPackageLoaded()   │        │ onSystemServerLoaded()      │ │
│   │ (for each app)      │        │ (once for system_server)    │ │
│   │                     │        │                              │ │
│   │ Handler.hook(lpp)   │        │ SystemServerHookLoader      │ │
│   └─────────────────────┘        └─────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────┘
```

---

## 2. XposedService - Module App IPC

### Kết nối với XposedService (trong Activity)

```kotlin
package [package]

import android.app.Activity
import android.os.Bundle
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.libxposed.service.XposedServiceHelper.OnServiceListener

class MainActivity : Activity() {
    private var xposedService: XposedService? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register listener cho XposedService
        XposedServiceHelper.registerListener(object : OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                xposedService = service
                onServiceConnected(service)
            }
            
            override fun onServiceDied(service: XposedService) {
                xposedService = null
                // Handle reconnection nếu cần
            }
        })
    }
    
    private fun onServiceConnected(service: XposedService) {
        // Lấy thông tin framework
        val frameworkName = service.frameworkName
        val frameworkVersion = service.frameworkVersion
        val apiVersion = service.apiVersion
        
        // Lấy scope (list apps được hook)
        val scope = service.scope
        
        // Remote Preferences (read/write từ module app)
        val prefs = service.getRemotePreferences("config")
        prefs.edit()
            .putBoolean("feature_enabled", true)
            .putString("api_key", "xxx")
            .apply()
        
        // Remote Files
        val files = service.listRemoteFiles()
        val fd = service.openRemoteFile("data.json")
    }
    
    fun requestAddToScope(packageName: String) {
        xposedService?.requestScope(packageName, object : XposedService.ScopeRequestCallback {
            override fun onScopeRequestAccepted(packageName: String) {
                // User accepted
            }
            
            override fun onScopeRequestDenied(packageName: String, reason: Int) {
                // User denied or error
            }
        })
    }
}
```

### AndroidManifest.xml - XposedProvider

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <application
        android:label="@string/app_name"
        android:theme="@style/Theme.App">
        
        <!-- XposedProvider để nhận Binder từ framework -->
        <provider
            android:name="io.github.libxposed.service.XposedProvider"
            android:authorities="${applicationId}.XposedService"
            android:exported="true" />
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
    </application>
</manifest>
```

---

## 3. Remote Preferences - Settings Sync

### Trong Module App (Read/Write)

```kotlin
class SettingsActivity : Activity() {
    private var xposedService: XposedService? = null
    
    fun saveSettings(enabled: Boolean, threshold: Int) {
        xposedService?.getRemotePreferences("settings")?.edit()
            ?.putBoolean("enabled", enabled)
            ?.putInt("threshold", threshold)
            ?.apply()
    }
    
    fun loadSettings(): Pair<Boolean, Int> {
        val prefs = xposedService?.getRemotePreferences("settings")
        return Pair(
            prefs?.getBoolean("enabled", false) ?: false,
            prefs?.getInt("threshold", 50) ?: 50
        )
    }
}
```

### Trong Hooked Process (Read-Only via XposedModule)

```kotlin
class TargetHandler : IHook() {
    override fun onHook() {
        // Access remote preferences từ XposedModule
        // Lưu ý: Cần reference đến XposedModule instance
        
        findClass("com.example.Activity").hookAllCAfter { param ->
            val activity = param.thisObject
            
            // Đọc settings
            val prefs = getRemotePreferences("settings")
            val enabled = prefs.getBoolean("enabled", false)
            
            if (enabled) {
                // Apply feature
            }
        }
    }
    
    // Helper method để access remote prefs
    private fun getRemotePreferences(name: String) = 
        Xposed.xposedInterface.getRemotePreferences(name)
}
```

---

## 4. Scope Management

### scope.list và staticScope

**META-INF/xposed/scope.list:**
```
# Các apps được hook
org.telegram.messenger
com.android.systemui
com.example.target
android
```

**META-INF/xposed/module.prop:**
```properties
minApiVersion=100
targetApiVersion=100
staticScope=true
```

### Dynamic Scope từ Code

```kotlin
class MainActivity : Activity() {
    private var xposedService: XposedService? = null
    
    fun addAppToScope(packageName: String) {
        xposedService?.requestScope(packageName, object : XposedService.ScopeRequestCallback {
            override fun onScopeRequestAccepted(packageName: String) {
                Toast.makeText(this@MainActivity, 
                    "$packageName added to scope", Toast.LENGTH_SHORT).show()
            }
            
            override fun onScopeRequestDenied(packageName: String, reason: Int) {
                val msg = when (reason) {
                    XposedService.SCOPE_REQUEST_DENIED_USER -> "User denied"
                    XposedService.SCOPE_REQUEST_DENIED_NOT_INSTALLED -> "App not installed"
                    else -> "Unknown reason"
                }
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        })
    }
    
    fun getCurrentScope() {
        val scope = xposedService?.scope ?: emptyList()
        // Display scope list
    }
}
```

---

## 5. Scope Event Listener

```kotlin
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        XposedServiceHelper.registerListener(object : OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                // Listen for scope changes
                service.registerScopeEventListener(object : XposedService.OnScopeEventListener {
                    override fun onScopeEnlarged(packageName: String) {
                        // New app added to scope
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, 
                                "Added: $packageName", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    override fun onScopeReduced(packageName: String) {
                        // App removed from scope
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, 
                                "Removed: $packageName", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
            
            override fun onServiceDied(service: XposedService) {
                // Handle disconnection
            }
        })
    }
}
```

---

## 6. Hot Loading (System Server)

### HotLoadHook Interface

```kotlin
package [package].bridge

/**
 * Interface cho hooks hỗ trợ hot reload.
 * Sử dụng trong system_server để reload module không cần reboot.
 */
abstract class HotLoadHook {
    /**
     * Called khi module được load/reload.
     * Register hooks tại đây.
     */
    abstract fun onLoad(classLoader: ClassLoader)
    
    /**
     * Called trước khi reload.
     * Cleanup resources, unregister receivers, etc.
     */
    abstract fun onUnload()
}
```

### SystemServerHookLoader

```kotlin
package [package].system_server

import io.github.libxposed.api.XposedModuleInterface.SystemServerLoadedParam
import [package].bridge.Xposed
import java.lang.ref.WeakReference

/**
 * Loader cho system_server hooks với hot reload support.
 */
object SystemServerHookLoader {
    // Tracking để detect memory leaks
    private val recycledHooks = mutableListOf<WeakReference<HotLoadHook>>()
    
    private var currentHook: HotLoadHook? = null
    private var currentPath: String? = null
    
    fun hook(param: SystemServerLoadedParam) {
        // Initial load
        val classLoader = param.classLoader
        loadHook(classLoader)
    }
    
    @Synchronized
    private fun loadHook(classLoader: ClassLoader) {
        // Unload current nếu có
        currentHook?.let { old ->
            runCatching { old.onUnload() }
                .onFailure { Xposed.log("Unload failed", it) }
            recycledHooks.add(WeakReference(old))
            Xposed.log("Unloaded: $old")
        }
        
        currentHook = null
        
        // Load new
        runCatching {
            val hook = SystemServerHandler()
            currentHook = hook
            hook.onLoad(classLoader)
            Xposed.log("Loaded: $hook")
        }.onFailure {
            Xposed.log("Load failed", it)
            currentHook = null
        }
    }
    
    fun requestReload(classLoader: ClassLoader) {
        loadHook(classLoader)
    }
    
    fun requestUnload() {
        currentHook?.onUnload()
        currentHook = null
    }
    
    fun reportOldHooks(): List<String> {
        recycledHooks.removeIf { it.get() == null }
        return recycledHooks.mapNotNull { it.get()?.toString() }
    }
}
```

### SystemServerHandler

```kotlin
package [package].system_server

import [package].bridge.HotLoadHook
import [package].bridge.Xposed

/**
 * Handler cho system_server hooks.
 * Implements HotLoadHook để hỗ trợ reload.
 */
class SystemServerHandler : HotLoadHook() {
    private val receivers = mutableListOf<Any>()
    
    override fun onLoad(classLoader: ClassLoader) {
        Xposed.log("SystemServerHandler.onLoad")
        
        // Register hooks
        registerSystemHooks(classLoader)
        
        // Register broadcast receivers nếu cần
        // registerReceivers()
    }
    
    override fun onUnload() {
        Xposed.log("SystemServerHandler.onUnload")
        
        // Cleanup receivers
        receivers.clear()
        
        // Cleanup khác nếu cần
    }
    
    private fun registerSystemHooks(classLoader: ClassLoader) {
        runCatching {
            val activityManagerClass = classLoader.loadClass(
                "com.android.server.am.ActivityManagerService"
            )
            
            // Hook methods...
        }.onFailure {
            Xposed.log("Failed to hook AMS", it)
        }
    }
}
```

---

## 7. Logging Utilities

```kotlin
package [package]

import [package].bridge.Xposed

private const val TAG = "MyModule"

fun logI(msg: String) {
    if (Xposed.isInitialized) {
        Xposed.log("$TAG: [INFO] $msg")
    }
}

fun logE(msg: String, t: Throwable? = null) {
    if (Xposed.isInitialized) {
        if (t != null) {
            Xposed.log("$TAG: [ERROR] $msg", t)
        } else {
            Xposed.log("$TAG: [ERROR] $msg")
        }
    }
}

fun logD(msg: String) {
    if (Xposed.isInitialized) {
        Xposed.log("$TAG: [DEBUG] $msg")
    }
}

fun logW(msg: String) {
    if (Xposed.isInitialized) {
        Xposed.log("$TAG: [WARN] $msg")
    }
}
```

---

## 8. Best Practices

### Security
- **Sử dụng XposedService** cho IPC thay vì custom Binder
- **Validate preferences** trước khi sử dụng
- **Không expose sensitive data** qua remote preferences

### Performance
- **Lazy initialization** khi có thể
- **Cache obfuscation tables** để tránh DexKit scan mỗi lần
- **Sử dụng WeakReference** để tránh memory leaks

### Reliability
- **Error handling** cho tất cả hooks
- **Graceful degradation** khi service unavailable
- **Logging** đầy đủ để debug

### Scope Management
- **staticScope=true** cho module đơn giản
- **Dynamic scope** chỉ khi thực sự cần thiết
- **Listen scope events** để update UI

---

## 9. Complete Example: Settings-Driven Feature

### Handler Implementation

```kotlin
class FeatureHandler(
    private val prefsCallback: () -> SharedPreferences?
) : IHook() {
    
    override fun onHook() {
        val prefs = prefsCallback() ?: return
        val featureEnabled = prefs.getBoolean("feature_x_enabled", false)
        
        if (!featureEnabled) {
            logI("Feature X disabled, skipping hooks")
            return
        }
        
        findClass("com.target.FeatureClass").hookAllAfter("showPopup") { param ->
            val threshold = prefs.getInt("popup_threshold", 5)
            val count = param.args[0] as? Int ?: 0
            
            if (count < threshold) {
                param.result = null // Block popup
                logI("Popup blocked: count=$count < threshold=$threshold")
            }
        }
    }
}
```

### Usage in ModuleMain

```kotlin
class ModuleMain(base: XposedInterface, param: ModuleLoadedParam) : XposedModule(base, param) {
    
    init {
        Xposed.init(base)
    }
    
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return
        
        val lpp = LoadPackageParam(param)
        
        if (param.packageName == "com.target.app") {
            FeatureHandler(prefsCallback = {
                getRemotePreferences("settings")
            }).hook(lpp)
        }
    }
}
```

---

## ProGuard Rules

```proguard
# Keep module entry
-keep class [package].ModuleMain {
    public <init>(...);
}

# Keep HotLoadHook implementations
-keep class * extends [package].bridge.HotLoadHook { *; }

# Keep for XposedService
-keep class io.github.libxposed.service.XposedProvider

# Keep bridge layer
-keep class [package].bridge.** { *; }

# Keep DynamicHooker với R8 annotations
-keep @io.github.libxposed.api.annotations.XposedHooker class * {
    @io.github.libxposed.api.annotations.BeforeInvocation public static *;
    @io.github.libxposed.api.annotations.AfterInvocation public static *;
}
```
