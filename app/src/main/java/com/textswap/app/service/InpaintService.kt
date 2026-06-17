package com.textswap.app.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.textswap.app.TextSwapApp
import com.textswap.app.model.TextRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 背景修复服务（Inpainting）
 *
 * 双轨策略：
 * - AI：ONNX Runtime + LaMa-Tiny → 复杂纹理/照片背景
 * - Fallback：周边像素采样填充 → 纯色/渐变背景（截图类效果好）
 *
 * 模型：assets/models/lama_tiny.onnx（运行 download_models.sh 下载）
 */
object InpaintService {

    @Volatile var aiEnabled: Boolean = false; private set

    fun init() {
        try { TextSwapApp.instance.assets.openFd("models/lama_tiny.onnx").close(); aiEnabled = true }
        catch (_: Exception) { aiEnabled = false }
    }

    suspend fun eraseText(bitmap: Bitmap, region: TextRegion, paddingPx: Int = 4): Bitmap =
        withContext(Dispatchers.Default) {
            val rect = expandRect(region.boundingBox, paddingPx, bitmap.width, bitmap.height)
            if (aiEnabled) {
                try { return@withContext aiInpaint(bitmap, rect) }
                catch (_: Exception) { /* fall through */ }
            }
            fallbackErase(bitmap, rect)
        }

    // ── AI: ONNX Runtime + LaMa ──

    private fun aiInpaint(bitmap: Bitmap, rect: Rect): Bitmap {
        val env = com.microsoft.onnxruntime.OrtEnvironment.getEnvironment()
        val session = env.createSession(
            TextSwapApp.instance.assets.openFd("models/lama_tiny.onnx").use { fd ->
                val buffer = java.nio.ByteBuffer.allocateDirect(fd.length.toInt())
                java.io.FileInputStream(fd.fileDescriptor).channel.use { ch ->
                    ch.position(fd.startOffset)
                    ch.read(buffer)
                }
                buffer.rewind()
                buffer
            }
        )

        // 裁剪区域（含上下文）
        val margin = 32
        val cropRect = Rect(
            max(0, rect.left - margin), max(0, rect.top - margin),
            min(bitmap.width, rect.right + margin), min(bitmap.height, rect.bottom + margin)
        )
        val crop = Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top,
            cropRect.right - cropRect.left, cropRect.bottom - cropRect.top)

        val inSize = 256
        val scaled = Bitmap.createScaledBitmap(crop, inSize, inSize, true)

        // image tensor: [1,3,256,256] float32 [-1,1]
        val imgData = FloatArray(3 * inSize * inSize)
        val px = IntArray(inSize * inSize); scaled.getPixels(px, 0, inSize, 0, 0, inSize, inSize)
        for (i in px.indices) {
            imgData[i] = (Color.red(px[i]) / 127.5f - 1f)
            imgData[inSize * inSize + i] = (Color.green(px[i]) / 127.5f - 1f)
            imgData[2 * inSize * inSize + i] = (Color.blue(px[i]) / 127.5f - 1f)
        }

        // mask tensor: [1,1,256,256] 文字区域=1
        val maskData = FloatArray(inSize * inSize)
        val cropW = cropRect.right - cropRect.left
        val cropH = cropRect.bottom - cropRect.top
        val rx = ((rect.left - cropRect.left).toFloat() / cropW * inSize).toInt()
        val ry = ((rect.top - cropRect.top).toFloat() / cropH * inSize).toInt()
        val rw = ((rect.right - rect.left).toFloat() / cropW * inSize).toInt().coerceAtLeast(2)
        val rh = ((rect.bottom - rect.top).toFloat() / cropH * inSize).toInt().coerceAtLeast(2)
        for (y in ry until min(ry + rh, inSize))
            for (x in rx until min(rx + rw, inSize))
                maskData[y * inSize + x] = 1f

        val imgTensor = com.microsoft.onnxruntime.OnnxTensor.createTensor(env, imgData,
            longArrayOf(1, 3, inSize.toLong(), inSize.toLong()))
        val maskTensor = com.microsoft.onnxruntime.OnnxTensor.createTensor(env, maskData,
            longArrayOf(1, 1, inSize.toLong(), inSize.toLong()))

        val inputs = mapOf("image" to imgTensor, "mask" to maskTensor)
        val result = session.run(inputs)
        @Suppress("UNCHECKED_CAST")
        val outRaw = result.getValue("output").value as Array<Array<Array<FloatArray>>>

        // 重建输出 bitmap
        val outPx = IntArray(inSize * inSize)
        for (y in 0 until inSize) for (x in 0 until inSize) {
            val r = ((outRaw[0][0][y][x] + 1f) * 127.5f).toInt().coerceIn(0, 255)
            val g = ((outRaw[0][1][y][x] + 1f) * 127.5f).toInt().coerceIn(0, 255)
            val b = ((outRaw[0][2][y][x] + 1f) * 127.5f).toInt().coerceIn(0, 255)
            outPx[y * inSize + x] = Color.rgb(r, g, b)
        }
        val outBmp = Bitmap.createBitmap(outPx, inSize, inSize, Bitmap.Config.ARGB_8888)
        val outScaled = Bitmap.createScaledBitmap(outBmp, crop.width, crop.height, true)

        val finalResult = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(finalResult).drawBitmap(outScaled, cropRect.left.toFloat(), cropRect.top.toFloat(), null)
        result.close()
        session.close()
        return finalResult
    }

    // ── Fallback: 像素采样 ──

    private fun fallbackErase(bitmap: Bitmap, rect: Rect): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val bgType = detectBackgroundType(bitmap, rect)
        fillBackground(canvas, bitmap, rect, bgType)
        return result
    }

    private fun expandRect(r: Rect, p: Int, mw: Int, mh: Int) =
        Rect(max(0, r.left - p), max(0, r.top - p), min(mw, r.right + p), min(mh, r.bottom + p))

    private fun detectBackgroundType(bmp: Bitmap, rect: Rect): Int {
        val s = mutableListOf<Int>()
        if (rect.top > 0) for (x in rect.left until rect.right) s.add(bmp.getPixel(x, rect.top - 1))
        if (rect.bottom < bmp.height - 1) for (x in rect.left until rect.right) s.add(bmp.getPixel(x, rect.bottom + 1))
        if (rect.left > 0) for (y in rect.top until rect.bottom) s.add(bmp.getPixel(rect.left - 1, y))
        if (rect.right < bmp.width - 1) for (y in rect.top until rect.bottom) s.add(bmp.getPixel(rect.right + 1, y))
        if (s.isEmpty()) return 0
        val mr = s.map { Color.red(it) }.average(); val mg = s.map { Color.green(it) }.average()
        val mb = s.map { Color.blue(it) }.average()
        val v = s.map { val dr = Color.red(it) - mr; val dg = Color.green(it) - mg; val db = Color.blue(it) - mb; dr * dr + dg * dg + db * db }.average()
        return if (v < 400.0) 0 else 1 // 0=SOLID, 1=GRADIENT
    }

    private fun fillBackground(canvas: Canvas, bmp: Bitmap, rect: Rect, type: Int) {
        if (type == 0) {
            val c = bmp.getPixel(max(0, rect.left - 2), max(0, rect.top - 2))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.FILL }
            canvas.drawRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), p)
        } else {
            val tc = if (rect.top > 0) bmp.getPixel((rect.left + rect.right) / 2, rect.top - 2) else Color.WHITE
            val bc = if (rect.bottom < bmp.height - 1) bmp.getPixel((rect.left + rect.right) / 2, rect.bottom + 2) else Color.WHITE
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = android.graphics.LinearGradient(rect.left.toFloat(), rect.top.toFloat(),
                    rect.left.toFloat(), rect.bottom.toFloat(), tc, bc, android.graphics.Shader.TileMode.CLAMP)
            }
            canvas.drawRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), p)
        }
    }
}