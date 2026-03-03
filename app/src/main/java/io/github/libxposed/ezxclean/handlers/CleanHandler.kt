package io.github.libxposed.ezxclean.handlers

import android.app.Application
import android.content.Context
import io.github.libxposed.ezxclean.arch.IHook
import io.github.libxposed.ezxclean.arch.hookAfter
import io.github.libxposed.ezxclean.logE
import io.github.libxposed.ezxclean.logI
import java.io.File
import java.util.concurrent.Executors

/**
 * CleanHandler (Steel Core Version)
 * Tuân thủ nghiêm ngặt 5 quy tắc bất biến:
 * 1. Scope Isolation: Check package trước khi hook.
 * 2. Data Hierarchy: Dùng loadPackageParam và Context, KHÔNG dùng Hidden API thừa thãi.
 * 3. DSL Architecture: Dùng hookAfter.
 * 4. Resiliency: Error handling toàn diện, Background thread priority thấp.
 * 5. Logging: Log chuẩn format.
 */
class CleanHandler : IHook() {

    companion object {
        private const val THREAD_NAME = "EzXClean-Cleaner"
        
        // Rule 4: Background Execution với Low Priority để không ảnh hưởng Cold Start của App
        private val cleanupExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, THREAD_NAME).apply {
                priority = Thread.MIN_PRIORITY // Ưu tiên thấp nhất
                isDaemon = true
            }
        }
    }

    override fun onHook() {
        // Rule 1: Scope Isolation (The Golden Rule)
        // Loại bỏ chính module, System Server và SystemUI (an toàn 2 lớp dù Entry đã check)
        val pkg = loadPackageParam.packageName
        if (pkg == "io.github.libxposed.ezxclean" || 
            pkg == "android" ||
            pkg == "com.android.systemui") return

        // Rule 3: DSL Architecture Standard
        // Hook vào Application.onCreate để đảm bảo Context đã sẵn sàng
        Application::class.java.hookAfter("onCreate") { param ->
            // Sử dụng safe cast để tránh crash
            val app = param.thisObject as? Application ?: return@hookAfter

            // Rule 4: Resiliency - Zero Crash Policy
            runCatching {
                // Rule 2: Data Hierarchy - Level 1 (IHook Data)
                // Không gọi ActivityThread hay Reflection ở đây
                val pkgName = loadPackageParam.packageName
                
                // Chỉ log Info một lần khi init thành công
                logI("CachePurge Init: $pkgName")

                // Chuyển sang Background Thread ngay lập tức
                performCleanup(app)
            }.onFailure {
                logE("Failed to init CleanHandler hook", it)
            }
        }
    }

    private fun performCleanup(context: Context) {
        cleanupExecutor.execute {
            // Rule 4: Resiliency - Bắt lỗi trong Thread riêng để không crash app
            runCatching {
                val start = System.currentTimeMillis()
                var freedBytes = 0L
                var fileCount = 0

                // Rule 2: Data Hierarchy - Level 2 (Context Data)
                // Hệ điều hành tự động trả về path đúng cho User ID hiện tại (Dual Messenger, Work Profile...)
                val cacheDirs = listOfNotNull(
                    context.externalCacheDir,
                    context.cacheDir,
                    context.codeCacheDir
                )

                cacheDirs.forEach { dir ->
                    if (dir.exists()) {
                        val (bytes, count) = deleteRecursively(dir)
                        freedBytes += bytes
                        fileCount += count
                    }
                }

                // Rule 5: Logging - Chỉ log khi có hành động thực tế để tránh spam
                if (fileCount > 0) {
                    val time = System.currentTimeMillis() - start
                    logI("Cleaned: $fileCount files (${freedBytes / 1024} KB) in ${time}ms")
                }
            }.onFailure {
                // Silent fail hoặc logE tùy mức độ nghiêm trọng. 
                // Với lỗi IO, logE là phù hợp để debug nhưng không crash.
                logE("CleanHandler: Background cleanup error", it)
            }
        }
    }

    /**
     * Hàm đệ quy xóa file an toàn.
     * Chỉ xóa FILES, giữ nguyên toàn bộ cấu trúc thư mục để tránh lỗi permission.
     * Trả về Pair<BytesDeleted, FileCount>
     */
    private fun deleteRecursively(file: File): Pair<Long, Int> {
        var size = 0L
        var count = 0
        
        if (!file.exists()) return 0L to 0

        if (file.isDirectory) {
            // Duyệt đệ quy vào thư mục con
            file.listFiles()?.forEach { child ->
                val (childSize, childCount) = deleteRecursively(child)
                size += childSize
                count += childCount
            }
            // KHÔNG xóa thư mục - giữ nguyên cấu trúc
        } else {
            // Chỉ xóa files
            val length = file.length()
            if (file.delete()) {
                size += length
                count++
            }
        }
        
        return size to count
    }
}
