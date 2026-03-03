package io.github.libxposed.ezxclean

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.ezxclean.bridge.LoadPackageParam
import io.github.libxposed.ezxclean.bridge.Xposed
import io.github.libxposed.ezxclean.handlers.VisionBootstrapHandler
import io.github.libxposed.ezxclean.logE
import io.github.libxposed.ezxclean.logI

/**
 * Claw-G Vision Module Entry
 * Tuân thủ LibXposed API 100 guard rails và scope isolation.
 */
internal lateinit var module: ModuleMain

class ModuleMain(base: XposedInterface, param: ModuleLoadedParam) : XposedModule(base, param) {

    init {
        module = this
        Xposed.init(base)
        log("Claw-G Vision Module loaded at ${param.processName}")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)

        if (!param.isFirstPackage) return
        if (param.packageName.startsWith("io.github.libxposed")) return
        if (param.packageName == "android") return

        logI("Package loaded: ${param.packageName}")

        runCatching {
            VisionBootstrapHandler().hook(LoadPackageParam(param))
        }.onFailure {
            logE("Failed to bootstrap vision", it)
        }
    }
}
