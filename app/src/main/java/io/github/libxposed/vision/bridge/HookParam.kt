package io.github.libxposed.vision.bridge

import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import java.lang.reflect.Member

class HookParam private constructor(
    private val beforeCallback: BeforeHookCallback?,
    private val afterCallback: AfterHookCallback?,
    private val isBefore: Boolean
) {
    companion object {
        @JvmStatic
        fun fromBefore(callback: BeforeHookCallback) = HookParam(callback, null, true)
        
        @JvmStatic
        fun fromAfter(callback: AfterHookCallback) = HookParam(null, callback, false)
    }
    
    val method: Member 
        get() = beforeCallback?.member ?: afterCallback!!.member
    
    val thisObject: Any? 
        get() = beforeCallback?.thisObject ?: afterCallback?.thisObject
    
    val args: Array<Any?> 
        get() = beforeCallback?.args ?: afterCallback!!.args
    
    var result: Any?
        get() = afterCallback?.result
        set(value) {
            if (isBefore) {
                beforeCallback?.returnAndSkip(value)
            } else {
                afterCallback?.setResult(value)
            }
        }
    
    var throwable: Throwable?
        get() = afterCallback?.throwable
        set(value) {
            if (isBefore && value != null) {
                beforeCallback?.throwAndSkip(value)
            } else if (!isBefore) {
                afterCallback?.setThrowable(value)
            }
        }
    
    val isSkipped: Boolean
        get() = afterCallback?.isSkipped ?: false
}
