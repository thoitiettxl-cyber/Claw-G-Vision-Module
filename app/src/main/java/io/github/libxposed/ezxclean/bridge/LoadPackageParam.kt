package io.github.libxposed.ezxclean.bridge

import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam as ApiParam

class LoadPackageParam(private val param: ApiParam) {
    val packageName: String get() = param.packageName
    val classLoader: ClassLoader get() = param.classLoader
    val isFirstPackage: Boolean get() = param.isFirstPackage
    val original: ApiParam get() = param
}
