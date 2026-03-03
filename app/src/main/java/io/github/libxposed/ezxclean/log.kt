package io.github.libxposed.ezxclean

import io.github.libxposed.ezxclean.bridge.Xposed

/**
 * Logging utilities với format chuẩn
 * Tag: EzXClean - filter với: su -c "logcat -s EzXClean"
 */
private const val TAG = "EzXClean"

fun logI(msg: String) {
    Xposed.log("$TAG: [INFO] $msg")
}

fun logE(msg: String, t: Throwable? = null) {
    if (t != null) {
        Xposed.log("$TAG: [ERROR] $msg", t)
    } else {
        Xposed.log("$TAG: [ERROR] $msg")
    }
}

fun logD(msg: String) {
    Xposed.log("$TAG: [DEBUG] $msg")
}
