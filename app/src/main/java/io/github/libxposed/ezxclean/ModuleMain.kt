package io.github.libxposed.ezxclean

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.ezxclean.bridge.LoadPackageParam
import io.github.libxposed.ezxclean.bridge.Xposed
import io.github.libxposed.ezxclean.handlers.CleanHandler

/**
 * EzXClean Module Entry
 * Tuân thủ Modern Xposed API 100 standards
 */
internal lateinit var module: ModuleMain

class ModuleMain(base: XposedInterface, param: ModuleLoadedParam) : XposedModule(base, param) {

    init {
        module = this
        // Rule 2: Initialize Xposed bridge
        Xposed.init(base)
        log("EzXClean Module loaded at ${param.processName}")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        
        // Rule 1: Scope Isolation - Skip non-first packages
        if (!param.isFirstPackage) return
        
        // Rule 1: Scope Isolation - Skip module itself
        if (param.packageName == "io.github.libxposed.ezxclean") return
        
        // Rule 1: Scope Isolation - Skip system server
        if (param.packageName == "android") return
        
        logI("Package loaded: ${param.packageName}")
        
        // Rule 4: Error handling
        runCatching {
            // Integrate CleanHandler using IHook pattern
            CleanHandler().hook(LoadPackageParam(param))
        }.onFailure {
            logE("Failed to load CleanHandler", it)
        }
    }
}
