package io.github.libxposed.vision.action

import android.app.Application

sealed interface VisionAction {
    val type: String

    fun validate(): Boolean
    fun perform(app: Application)
}

class TapAction(private val x: Int, private val y: Int) : VisionAction {
    override val type: String = "tap"

    override fun validate(): Boolean = x >= 0 && y >= 0

    override fun perform(app: Application) {
        // no-op stub
    }
}
