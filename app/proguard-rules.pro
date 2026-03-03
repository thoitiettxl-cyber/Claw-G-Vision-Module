# Xposed Module Entry
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep class io.github.libxposed.ezxclean.ModuleMain {
    public <init>(...);
    public void onPackageLoaded(...);
    public void onSystemServerLoaded(...);
}

# LibXposed API 100 - Keep all Hookers
# This replaces the old @XposedHooker annotation rules
-keep class * implements io.github.libxposed.api.XposedInterface$Hooker {
    public static void before(...);
    public static void after(...);
    public static * before(...);
}

# Claw-G Vision Layers
# Bridge Layer - Must be kept for reflection wrappings
-keep class io.github.libxposed.ezxclean.bridge.** { *; }

# Arch Layer - IHook and utilities
-keep class io.github.libxposed.ezxclean.arch.** { *; }

# Handlers
-keep class io.github.libxposed.ezxclean.handlers.** { *; }

# Kotlin Standard Library
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}
-assumenosideeffects class java.util.Objects {
    public static ** requireNonNull(...);
}

# Keep Attributes
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes Signature
-keepattributes Exceptions

# Strip debug logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Keep HiddenApiBypass
-keep class org.lsposed.hiddenapibypass.** { *; }
-keep class stub.** { *; }

# Keep dummy annotations for R8 compatibility
-keep @interface io.github.libxposed.api.annotations.*
-keep @io.github.libxposed.api.annotations.XposedHooker class * {
    @io.github.libxposed.api.annotations.BeforeInvocation public static *;
    @io.github.libxposed.api.annotations.AfterInvocation public static *;
}

# Optimization
-repackageclasses
-allowaccessmodification
