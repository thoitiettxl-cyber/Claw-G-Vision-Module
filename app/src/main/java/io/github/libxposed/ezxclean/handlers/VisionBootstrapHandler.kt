package io.github.libxposed.ezxclean.handlers

import android.app.Application
import io.github.libxposed.ezxclean.arch.IHook
import io.github.libxposed.ezxclean.arch.hookAfter
import io.github.libxposed.ezxclean.bridge.LoadPackageParam
import io.github.libxposed.ezxclean.logE
import io.github.libxposed.ezxclean.logI
import io.github.libxposed.ezxclean.vision.action.ActionDispatcher
import io.github.libxposed.ezxclean.vision.model.VisionConfig
import io.github.libxposed.ezxclean.vision.overlay.OverlayController

class VisionBootstrapHandler : IHook() {

    override fun onHook() {
        val pkg = loadPackageParam.packageName
        if (!loadPackageParam.isFirstPackage) return
        if (pkg.startsWith("io.github.libxposed")) return
        if (pkg == "android") return

        Application::class.java.hookAfter("onCreate") { param ->
            val app = param.thisObject as? Application ?: return@hookAfter
            runCatching {
                val config = VisionConfig.default()
                OverlayController.initialize(app, config.overlayConfig)
                ActionDispatcher.bootstrap(app, config.actionConfig)
                logI("Vision bootstrap ready for $pkg")
            }.onFailure {
                logE("Vision bootstrap failed", it)
            }
        }
    }
}
