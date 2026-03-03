package io.github.libxposed.vision.action

import android.app.Application
import io.github.libxposed.vision.logE
import io.github.libxposed.vision.logI
import io.github.libxposed.vision.model.ActionConfig

object ActionDispatcher {
    private lateinit var app: Application
    private lateinit var config: ActionConfig

    fun bootstrap(application: Application, actionConfig: ActionConfig) {
        app = application
        config = actionConfig
        logI("ActionDispatcher initialized")
    }

    fun dispatch(action: VisionAction) {
        if (!::config.isInitialized) {
            logE("ActionDispatcher not bootstrapped")
            return
        }
        if (config.dispatchGuard == DispatchGuard.STRICT && !action.validate()) {
            logE("Blocked invalid action: ${action.type}")
            return
        }
        runCatching {
            logI("Dispatching action: ${action.type}")
            action.perform(app)
        }.onFailure {
            logE("Action dispatch failed", it)
        }
    }
}
