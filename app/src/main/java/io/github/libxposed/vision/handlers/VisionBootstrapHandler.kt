package io.github.libxposed.vision.handlers

import android.app.Application
import io.github.libxposed.vision.arch.IHook
import io.github.libxposed.vision.arch.hookAfter
import io.github.libxposed.vision.bridge.LoadPackageParam
import io.github.libxposed.vision.logE
import io.github.libxposed.vision.logI
import io.github.libxposed.vision.action.ActionDispatcher
import io.github.libxposed.vision.model.VisionConfig
import io.github.libxposed.vision.overlay.OverlayController

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
