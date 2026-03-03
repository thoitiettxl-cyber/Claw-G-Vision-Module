package io.github.libxposed.ezxclean.arch

import io.github.libxposed.ezxclean.bridge.LoadPackageParam
import io.github.libxposed.ezxclean.bridge.Xposed

abstract class IHook {
    lateinit var classLoader: ClassLoader
        private set
    
    lateinit var loadPackageParam: LoadPackageParam
        private set
    
    open fun hook(param: LoadPackageParam, loader: ClassLoader = param.classLoader) {
        loadPackageParam = param
        classLoader = loader
        try {
            onHook()
        } catch (t: Throwable) {
            Xposed.log("Hook failed: ${this.javaClass.simpleName}", t)
        }
    }

    fun subHook(hook: IHook) {
        hook.hook(loadPackageParam, classLoader)
    }

    protected fun findClass(name: String): Class<*> = 
        classLoader.loadClass(name)

    protected fun findClassOrNull(name: String): Class<*>? = 
        runCatching { classLoader.loadClass(name) }.getOrNull()

    protected abstract fun onHook()
}
