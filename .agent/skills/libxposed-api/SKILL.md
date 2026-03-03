---
name: libxposed-api
description: LibXposed API 100 và Service library cho phát triển Xposed module hiện đại. Sử dụng khi cần hook methods, constructors, quản lý scope, remote preferences, hoặc IPC giữa module app và framework. Bao gồm XposedModule, XposedInterface, Hooker pattern R8-compatible, XposedService, và XposedServiceHelper.
metadata:
  author: libxposed
  version: "100"
---

# LibXposed API 100 Skill

> **Self-Contained Knowledge Base** cho LibXposed API và Service library.
> Tất cả Hooker examples đều R8-compatible với dummy annotations.

## Quick Reference

| Component | Package | Mục đích |
|-----------|---------|----------|
| `XposedModule` | `io.github.libxposed.api` | Base class cho module entry |
| `XposedInterface` | `io.github.libxposed.api` | Hook API, logging, remote access |
| `Hooker` | `io.github.libxposed.api` | Interface cho hook classes |
| `XposedService` | `io.github.libxposed.service` | IPC client trong module app |
| `XposedServiceHelper` | `io.github.libxposed.service` | Service connection helper |
| `XposedProvider` | `io.github.libxposed.service` | ContentProvider nhận Binder |

### Gradle Dependencies

```kotlin
dependencies {
    compileOnly("io.github.libxposed:api:100")
    compileOnly(project(":libxposed-compat"))  // R8 compat annotations
    implementation("io.github.libxposed:service:100-1.0.0")
}
```

### R8 Compatibility (Mặc định)

| Yêu cầu | Mô tả |
|---------|-------|
| **Method naming** | Tên PHẢI là `before` và `after` |
| **Annotations** | `@XposedHooker` trên class, `@BeforeInvocation`/`@AfterInvocation` trên methods |
| **Dependency** | `compileOnly(project(":libxposed-compat"))` |

---

## Examples

### Module Entry Class

```java
public class MainHook extends XposedModule {
    
    public MainHook(@NonNull XposedInterface base, 
                    @NonNull ModuleLoadedParam param) {
        super(base, param);
        log("Module loaded: " + param.getProcessName());
    }
    
    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        if (!"com.target.app".equals(param.getPackageName())) return;
        if (!param.isFirstPackage()) return;
        
        try {
            Class<?> clazz = param.getClassLoader()
                .loadClass("com.target.app.TargetClass");
            Method method = clazz.getDeclaredMethod("targetMethod", String.class);
            hook(method, MyHooker.class);
        } catch (Exception e) {
            log("Hook failed", e);
        }
    }
}
```

### Hooker Class (R8 Compatible - Mặc định)

```java
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedInterface.BeforeHookCallback;
import io.github.libxposed.api.XposedInterface.AfterHookCallback;
import io.github.libxposed.api.annotations.XposedHooker;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.AfterInvocation;

@XposedHooker
public class MyHooker implements Hooker {
    
    @BeforeInvocation
    public static void before(@NonNull BeforeHookCallback callback) {
        Object[] args = callback.getArgs();
        // Modify args hoặc skip invocation
    }
    
    @AfterInvocation
    public static void after(@NonNull AfterHookCallback callback) {
        Object result = callback.getResult();
        // callback.setResult(newResult);
    }
}
```

### Hooker với Context

```java
@XposedHooker
public class TimingHooker implements Hooker {
    
    @BeforeInvocation
    public static Long before(@NonNull BeforeHookCallback callback) {
        return System.currentTimeMillis();
    }
    
    @AfterInvocation
    public static void after(@NonNull AfterHookCallback callback, Long startTime) {
        long elapsed = System.currentTimeMillis() - startTime;
        Log.d("Timing", callback.getMember().getName() + ": " + elapsed + "ms");
    }
}
```

### Service Connection (Module App)

```java
XposedServiceHelper.registerListener(new OnServiceListener() {
    @Override
    public void onServiceBind(@NonNull XposedService service) {
        String framework = service.getFrameworkName();
        List<String> scope = service.getScope();
        SharedPreferences prefs = service.getRemotePreferences("config");
    }
    
    @Override
    public void onServiceDied(@NonNull XposedService service) {
        // Handle reconnection
    }
});
```

---

## Patterns

### Pattern 1: Bypass Method Return

```java
@XposedHooker
public class BypassHooker implements Hooker {
    @BeforeInvocation
    public static void before(@NonNull BeforeHookCallback callback) {
        callback.returnAndSkip(true); // Skip original, return true
    }
}
```

### Pattern 2: Modify Arguments

```java
@XposedHooker
public class ArgModifyHooker implements Hooker {
    @BeforeInvocation
    public static void before(@NonNull BeforeHookCallback callback) {
        Object[] args = callback.getArgs();
        args[0] = "modified_value"; // Arrays are mutable
    }
}
```

### Pattern 3: Call Original Method

```java
@XposedHooker
public class WrapperHooker implements Hooker {
    @BeforeInvocation
    public static void before(@NonNull BeforeHookCallback callback) {
        try {
            Object result = module.invokeOrigin(
                (Method) callback.getMember(),
                callback.getThisObject(),
                "custom_arg"
            );
            callback.returnAndSkip(result);
        } catch (Exception e) {
            // Handle error
        }
    }
}
```

### Pattern 4: Remote Preferences Sync

```java
// In module app (read/write)
SharedPreferences prefs = service.getRemotePreferences("settings");
prefs.edit().putBoolean("enabled", true).apply();

// In hooked process (read-only)
SharedPreferences prefs = getRemotePreferences("settings");
boolean enabled = prefs.getBoolean("enabled", false);
```

### Pattern 5: libxposed-compat Module

Module structure cho dummy annotations:

```
libxposed-compat/
├── build.gradle
└── src/main/java/io/github/libxposed/api/annotations/
    ├── XposedHooker.java
    ├── BeforeInvocation.java
    └── AfterInvocation.java
```

**build.gradle:**
```groovy
plugins {
    id 'com.android.library'
}
android {
    namespace 'io.github.libxposed'
    compileSdk 36
    defaultConfig { minSdk 24 }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_21
        targetCompatibility JavaVersion.VERSION_21
    }
}
```

**Annotations (copy exact):**
```java
// XposedHooker.java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.TYPE_USE})
public @interface XposedHooker {}

// BeforeInvocation.java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BeforeInvocation {}

// AfterInvocation.java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AfterInvocation {}
```

---

## AI Workflow

### Khi Tạo Module Mới

1. **Tạo entry class** extends `XposedModule`
2. **Tạo `libxposed-compat`** module với 3 annotations
3. **Thêm dependencies**: `compileOnly(project(":libxposed-compat"))`
4. **Override `onPackageLoaded()`** để filter target package
5. **Tạo Hooker classes** với `@XposedHooker`, `@BeforeInvocation`, `@AfterInvocation`
6. **Khai báo entry** trong `assets/xposed_init`
7. **Thêm ProGuard rules**

### Khi Tạo Hooker Class

1. Add `@XposedHooker` annotation trên class
2. Implement `Hooker` interface
3. Tên method **PHẢI là** `before` và `after`
4. Add `@BeforeInvocation` và `@AfterInvocation` annotations

### ProGuard Rules (Bắt Buộc)

```proguard
# Keep XposedModule entry
-keep class * extends io.github.libxposed.api.XposedModule {
    public <init>(io.github.libxposed.api.XposedInterface, 
                  io.github.libxposed.api.XposedModuleInterface$ModuleLoadedParam);
}

# Keep all Hooker classes
-keep class * implements io.github.libxposed.api.XposedInterface$Hooker {
    public static void before(...);
    public static void after(...);
    public static ** before(...);
}

# Keep dummy annotations
-keep @interface io.github.libxposed.api.annotations.*
-keep @io.github.libxposed.api.annotations.XposedHooker class * {
    @io.github.libxposed.api.annotations.BeforeInvocation public static *;
    @io.github.libxposed.api.annotations.AfterInvocation public static *;
}

# Keep XposedProvider
-keep class io.github.libxposed.service.XposedProvider
```

### Khi Cần IPC (Module App ↔ Framework)

1. **Thêm `XposedProvider`** vào AndroidManifest.xml:
```xml
<provider
    android:name="io.github.libxposed.service.XposedProvider"
    android:authorities="${applicationId}.XposedService"
    android:exported="true" />
```
2. **Call `XposedServiceHelper.registerListener()`** trong Activity/Application
3. **Sử dụng `XposedService`** để manage scope, preferences, files

---

## API Reference

### XposedInterface Methods

| Method | Description |
|--------|-------------|
| `hook(Method, Hooker)` | Hook method với priority mặc định |
| `hook(Method, int, Hooker)` | Hook method với priority tùy chỉnh |
| `hook(Constructor, Hooker)` | Hook constructor |
| `hookClassInitializer(Class, Hooker)` | Hook static initializer |
| `deoptimize(Method)` | Prevent JIT inline |
| `invokeOrigin(Method, Object, Object...)` | Gọi method gốc |
| `invokeSpecial(Method, Object, Object...)` | Gọi method non-virtual |
| `log(String)` | Log message |
| `getRemotePreferences(String)` | Lấy remote prefs (read-only) |
| `getFrameworkName()` | Tên framework |
| `getFrameworkPrivilege()` | Privilege level |

### BeforeHookCallback Methods

| Method | Description |
|--------|-------------|
| `getMember()` | Lấy Method/Constructor được hook |
| `getThisObject()` | Lấy `this` object (null nếu static) |
| `getArgs()` | Lấy arguments (có thể modify) |
| `returnAndSkip(Object)` | Skip invocation, return value |
| `throwAndSkip(Throwable)` | Skip invocation, throw exception |

### AfterHookCallback Methods

| Method | Description |
|--------|-------------|
| `getMember()` | Lấy Method/Constructor được hook |
| `getThisObject()` | Lấy `this` object |
| `getArgs()` | Lấy arguments |
| `getResult()` | Lấy return value |
| `getThrowable()` | Lấy exception nếu có |
| `isSkipped()` | Kiểm tra có bị skip không |
| `setResult(Object)` | Đặt return value mới |
| `setThrowable(Throwable)` | Throw exception |

### XposedService Methods

| Method | Description |
|--------|-------------|
| `getScope()` | Danh sách apps trong scope |
| `requestScope(String, Callback)` | Request thêm app vào scope |
| `getRemotePreferences(String)` | Lấy prefs (read/write) |
| `listRemoteFiles()` | Liệt kê files |
| `openRemoteFile(String)` | Mở file (read/write) |

### Constants

```java
// Priority
PRIORITY_HIGHEST = 10000
PRIORITY_DEFAULT = 50
PRIORITY_LOWEST = -10000

// Framework Privileges
FRAMEWORK_PRIVILEGE_ROOT = 0
FRAMEWORK_PRIVILEGE_CONTAINER = 1
FRAMEWORK_PRIVILEGE_APP = 2
FRAMEWORK_PRIVILEGE_EMBEDDED = 3
```

---

## See Also

- @[references/ARCHITECTURE.md] (184 lines)
  - `Module Loading Flow` #L1-49 - Sequence diagram
  - `Hook Execution Flow` #L24-49 - Hook lifecycle
  - `IPC Architecture` #L50-98 - XposedService diagrams
  - `Class Hierarchy` #L99-142 - XposedModule inheritance
  - `AndroidManifest` #L155-183 - Required metadata

- @[references/DEXPARSER.md] (201 lines)
  - `Core Interfaces` #L17-70 - DexParser, ID interfaces
  - `Visitor Pattern` #L71-123 - ClassVisitor, MethodVisitor
  - `Find Callers Example` #L124-165 - Usage pattern
  - `Use Cases Table` #L191-201 - Quick lookup
