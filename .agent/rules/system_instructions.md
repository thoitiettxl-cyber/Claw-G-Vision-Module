---
name: modern-xposed-api-100
trigger: 
  - "xposed"
  - "hook"
  - "libxposed"
  - "module"
  - "inject"
  - "XposedInterface"
  - "XposedModule"
  - "Hooker"
description: System Instructions cho phát triển Xposed module với LibXposed API 100. Tự động kích hoạt khi làm việc với Xposed-related tasks.
---

# Modern Xposed API 100 - System Instructions

> **AI Directive**: Tự động tuân thủ khi phát hiện các keywords trong trigger list.

## Quy Trình Suy Luận (Reasoning Process)

### 1. Phân Tích Mục Tiêu

Trước khi viết code, xác định rõ:

| Câu hỏi | Ví dụ |
|---------|-------|
| **Target app?** | `com.telegram.messenger`, `com.android.systemui` |
| **Target class?** | `ActivityThread`, `LaunchActivity` |
| **Target method?** | `sendMessage`, `onCreate` |
| **Hook purpose?** | Modify args, replace return, log calls |

### 2. Kiểm Tra Phiên Bản

```
✓ LibXposed API 100 (KHÔNG PHẢI XposedBridge legacy)
✓ libxposed-service 100-1.0.0 (cho IPC)
✓ libxposed-compat module (dummy annotations)
✓ HiddenApiBypass 6.1 (cho hidden APIs)
```

**❌ KHÔNG SỬ DỤNG:**
- `de.robv.android.xposed.*`
- `IXposedHookLoadPackage`
- `XC_MethodHook`
- `XposedHelpers`

### 3. Lựa Chọn Chiến Lược

| Scenario | Strategy | Skill Reference |
|----------|----------|-----------------|
| Hook single method | `Class.hookAfter()` | libxposed-utils |
| Hook all overloads | `Class.hookAllAfter()` | libxposed-utils |
| Bypass method | `callback.returnAndSkip()` | libxposed-api |
| Call original | `Member.callOrig()` | libxposed-utils |
| Access hidden API | `HiddenApiBypass` | libxposed-utils |
| Obfuscated app | `DexKit + ObfsUtils` | libxposed-utils |
| IPC từ module app | `XposedService` | libxposed-api |

### 4. Sinh Mã Chính Xác

**Bắt buộc tuân thủ:**

```kotlin
// ✅ ĐÚNG: R8-compatible Hooker
@XposedHooker
class MyHooker : Hooker {
    companion object {
        @BeforeInvocation
        @JvmStatic
        fun before(callback: BeforeHookCallback) { ... }
        
        @AfterInvocation
        @JvmStatic
        fun after(callback: AfterHookCallback) { ... }
    }
}

// ❌ SAI: Thiếu annotations, sai tên method
class MyHooker : Hooker {
    fun doBefore(callback: BeforeHookCallback) { ... }
}
```

### 5. Xác Minh

Sau khi sinh code, kiểm tra:

- [ ] Annotations đầy đủ (`@XposedHooker`, `@BeforeInvocation`, `@AfterInvocation`)
- [ ] Method names chính xác (`before`, `after`)
- [ ] ProGuard rules có trong `proguard-rules.pro`
- [ ] Entry class khai báo trong `assets/xposed_init`
- [ ] Dependencies đúng phiên bản

---

## Prompt Kích Hoạt

Khi phát hiện các keywords sau, **TỰ ĐỘNG** tra cứu skills:

| Keyword | Action |
|---------|--------|
| `xposed`, `hook`, `inject` | Đọc `libxposed-api/SKILL.md` |
| `IHook`, `hookAfter`, `HookUtils` | Đọc `libxposed-utils/SKILL.md` |
| `hidden api`, `HiddenApiBypass` | Đọc `libxposed-utils` #L269-314 (Pattern 9) |
| `DexKit`, `obfuscated` | Đọc `libxposed-utils` #L130-151 |
| `XposedService`, `IPC` | Đọc `libxposed-api` #L121-137 |

---

## 5 Immutable Rules

> **KHÔNG BAO GIỜ VI PHẠM** - Áp dụng cho mọi code generation

### Rule 1: R8-Compatible Annotations

```kotlin
// ✅ BẮT BUỘC trên Hooker class
@XposedHooker

// ✅ BẮT BUỘC trên hook methods
@BeforeInvocation / @AfterInvocation
```

### Rule 2: Method Naming Convention

```kotlin
// ✅ ĐÚNG
fun before(callback: BeforeHookCallback)
fun after(callback: AfterHookCallback)

// ❌ SAI - R8 sẽ strip
fun onBefore(callback: BeforeHookCallback)
fun handleAfter(callback: AfterHookCallback)
```

### Rule 3: Static Companion Methods

```kotlin
// ✅ ĐÚNG
companion object {
    @JvmStatic
    fun before(...) { }
}

// ❌ SAI
fun before(...) { }  // Non-static
```

### Rule 4: libxposed-compat Module

**Phải tồn tại:**
```
libxposed-compat/
├── build.gradle.kts
└── src/main/java/io/github/libxposed/api/annotations/
    ├── XposedHooker.java
    ├── BeforeInvocation.java
    └── AfterInvocation.java
```

### Rule 5: ProGuard Rules

```proguard
# BẮT BUỘC trong proguard-rules.pro
-keep @interface io.github.libxposed.api.annotations.*
-keep @io.github.libxposed.api.annotations.XposedHooker class * {
    @io.github.libxposed.api.annotations.BeforeInvocation public static *;
    @io.github.libxposed.api.annotations.AfterInvocation public static *;
}
```

---

## Self-Audit Checklist

Sau khi hoàn thành code, **BẮT BUỘC** thực hiện:

### Step 1: Đối Chiếu Rules

| Rule | Check | Status |
|------|-------|--------|
| R8 Annotations | `@XposedHooker`, `@BeforeInvocation`, `@AfterInvocation` | ◻️ |
| Method Names | `before`, `after` (không đổi tên) | ◻️ |
| Static Methods | `@JvmStatic` trong companion object | ◻️ |
| libxposed-compat | Module tồn tại với 3 annotations | ◻️ |
| ProGuard | Keep rules cho annotations | ◻️ |

### Step 2: Báo Cáo Vi Phạm

Nếu phát hiện vi phạm, format:

```
## Self-Audit Report

### Violations Found:
1. **Rule 2**: Method `onBefore` không đúng convention → Fix: Rename to `before`
2. **Rule 5**: Thiếu ProGuard rule → Fix: Add keep rule

### Fixes Applied:
- [x] Renamed `onBefore` → `before`
- [x] Added ProGuard rules
```

### Step 3: Final Code

Cung cấp phiên bản hoàn chỉnh với:
- ✅ 100% tuân thủ 5 Immutable Rules
- ✅ Dependencies đúng phiên bản
- ✅ ProGuard rules đầy đủ
- ✅ Entry class khai báo

---

## Library Quick Reference

### Dependencies

```kotlin
dependencies {
    compileOnly("io.github.libxposed:api:100")
    compileOnly(project(":libxposed-compat"))
    implementation("io.github.libxposed:service:100-1.0.0")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
}
```

### Skills Index

| Need | Skill | Section |
|------|-------|---------|
| Module entry | @[libxposed-api/SKILL.md] | #L47-73 |
| Hooker class | @[libxposed-api/SKILL.md] | #L75-100 |
| HookUtils DSL | @[libxposed-utils/SKILL.md] | #L365-377 |
| ReflectUtil | @[libxposed-utils/SKILL.md] | #L378-392 |
| HiddenApiBypass | @[libxposed-utils/SKILL.md] | #L394-405 |
| ProGuard rules | @[libxposed-api/SKILL.md] | #L269-294 |

---

## Error Patterns

### Common Mistakes và Fixes

| Error | Cause | Fix |
|-------|-------|-----|
| `IllegalArgumentException: Not a Hooker` | Missing `@XposedHooker` | Add annotation |
| Hook không hoạt động sau R8 | Method bị strip | Add ProGuard rules |
| `NoSuchMethodError` on hidden API | Missing bypass | Call `enableHiddenApiBypass()` |
| Callback registry empty | `Xposed.init()` chưa gọi | Initialize in constructor |

---

> **AI Commitment**: Khi làm việc với Xposed tasks, tôi cam kết:
> 1. Luôn tra cứu skills trước khi sinh code
> 2. Tuân thủ 100% 5 Immutable Rules
> 3. Thực hiện self-audit sau mỗi code block
> 4. Cung cấp final code đã verified
