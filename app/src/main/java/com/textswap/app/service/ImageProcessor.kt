package com.textswap.app.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.textswap.app.TextSwapApp
import com.textswap.app.model.ImageTask
import com.textswap.app.model.ReplaceTask
import com.textswap.app.model.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

object ImageProcessor {

    private val resultCache = ConcurrentHashMap<String, Bitmap>()

    fun getCachedBitmap(taskId: String): Bitmap? = resultCache[taskId]

    fun clearCache(taskId: String) { resultCache.remove(taskId)?.recycle() }

    fun clearAllCache() {
        resultCache.values.forEach { it.recycle() }; resultCache.clear()
        File(TextSwapApp.instance.cacheDir, "processed").let { if (it.exists()) it.listFiles()?.forEach { f -> f.delete() } }
    }

    /** 获取处理后图片的磁盘缓存目录 */
    private fun processedDir(): File =
        File(TextSwapApp.instance.cacheDir, "processed").also { if (!it.exists()) it.mkdirs() }

    suspend fun batchOcr(tasks: List<ImageTask>): List<ImageTask> = withContext(Dispatchers.IO) {
        tasks.map { task ->
            try {
                val bmp = loadBitmap(task)
                task.copy(textRegions = OcrService.recognize(bmp).getOrThrow(), status = TaskStatus.OCR_DONE)
            } catch (e: Exception) {
                task.copy(status = TaskStatus.OCR_FAILED, errorMessage = e.message ?: "OCR failed")
            }
        }
    }

    suspend fun batchReplace(
        tasks: List<ImageTask>,
        replaceTasks: List<ReplaceTask>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<ImageTask> = withContext(Dispatchers.Default) {
        val total = tasks.size
        val regionMap = buildRegionMap(tasks, replaceTasks)

        tasks.mapIndexed { index, task ->
            async {
                try {
                    val regionsToReplace = regionMap[task.id]
                    if (regionsToReplace.isNullOrEmpty()) {
                        onProgress(index + 1, total)
                        return@async task.copy(status = TaskStatus.DONE)
                    }
                    val bitmap = loadBitmap(task)
                    val analyzed = regionsToReplace.map { FontMatcher.analyzeRegion(bitmap, it) }
                    var current = bitmap
                    for (region in analyzed) {
                        val rule = replaceTasks.firstOrNull { it.regions.any { r -> r.id == region.id } } ?: continue
                        current = InpaintService.eraseText(current, region)
                        current = TextRenderer.render(current, region, rule.targetText)
                    }

                    // 保存到磁盘缓存
                    val cacheFile = File(processedDir(), "${task.id}.png")
                    FileOutputStream(cacheFile).use { current.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    resultCache[task.id] = current

                    onProgress(index + 1, total)
                    task.copy(status = TaskStatus.DONE, resultCachePath = cacheFile.absolutePath)
                } catch (e: Exception) {
                    onProgress(index + 1, total)
                    task.copy(status = TaskStatus.FAILED, errorMessage = e.message ?: "Replace failed")
                }
            }
        }.awaitAll()
    }

    private fun buildRegionMap(tasks: List<ImageTask>, replaceTasks: List<ReplaceTask>):
            Map<String, List<com.textswap.app.model.TextRegion>> {
        val map = mutableMapOf<String, MutableList<com.textswap.app.model.TextRegion>>()
        for (rt in replaceTasks) for (region in rt.regions) {
            val task = tasks.find { it.textRegions.any { r -> r.id == region.id } }
            if (task != null) map.getOrPut(task.id) { mutableListOf() }.add(region)
        }
        return map
    }

    private fun loadBitmap(task: ImageTask): Bitmap {
        val resolver = TextSwapApp.instance.contentResolver
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(resolver.openInputStream(task.uri), null, opts)
        val maxDim = 2048
        val sample = if (opts.outWidth > maxDim || opts.outHeight > maxDim)
            maxOf(opts.outWidth / maxDim, opts.outHeight / maxDim).coerceAtLeast(1) else 1
        return BitmapFactory.decodeStream(resolver.openInputStream(task.uri), null,
            BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalStateException("Failed to decode: ${task.uri}")
    }
}