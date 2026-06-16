package com.textswap.app.service

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.textswap.app.TextSwapApp
import com.textswap.app.model.TextRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.roundToInt

/**
 * 字体匹配服务
 * 优先 TFLite 字体分类 → 兜底像素采样
 * 模型：assets/models/font_classifier.tflite
 */
object FontMatcher {

    private val FONT_LABELS = arrayOf(
        "Unknown", "PingFang SC", "Helvetica", "Roboto", "SF Pro Display",
        "Noto Sans CJK", "Microsoft YaHei", "Arial", "Times New Roman", "Georgia",
        "Verdana", "STHeiti", "Hiragino Sans", "Segoe UI", "Open Sans",
        "Lato", "Montserrat", "Source Han Sans", "DIN", "Futura",
        "Gotham", "Baskerville", "Palatino", "Optima", "Didot", "Bodoni"
    )

    @Volatile private var interpreter: Interpreter? = null

    fun init() {
        try {
            interpreter = Interpreter(loadModel("models/font_classifier.tflite"))
        } catch (_: Exception) { interpreter = null }
    }

    suspend fun analyzeRegion(bitmap: Bitmap, region: TextRegion): TextRegion =
        withContext(Dispatchers.Default) {
            val rect = region.boundingBox
            val fontSize = (rect.height() * 0.75f)
            val textColor = sampleTextColor(bitmap, rect)
            val shadowColor = detectShadow(bitmap, rect, textColor)
            val fontName = classifyFont(bitmap, rect) ?: "PingFang SC"
            region.copy(fontSize = fontSize, fontName = fontName, textColor = textColor, shadowColor = shadowColor)
        }

    // ── TFLite 字体分类 ──

    private fun classifyFont(bitmap: Bitmap, rect: Rect): String? {
        val interp = interpreter ?: return null
        try {
            val cw = rect.width().coerceAtLeast(16); val ch = rect.height().coerceAtLeast(16)
            val cx = maxOf(0, rect.left - 4).coerceAtMost(bitmap.width - cw)
            val cy = maxOf(0, rect.top - 4).coerceAtMost(bitmap.height - ch)
            val crop = Bitmap.createBitmap(bitmap, cx, cy, cw, ch)
            val scaled = Bitmap.createScaledBitmap(crop, 128, 128, true)

            val input = ByteBuffer.allocateDirect(128 * 128 * 4)
            input.order(ByteOrder.nativeOrder())
            val px = IntArray(128 * 128); scaled.getPixels(px, 0, 128, 0, 0, 128, 128)
            for (p in px) {
                val gray = (Color.red(p) * 0.299f + Color.green(p) * 0.587f + Color.blue(p) * 0.114f) / 255f
                input.putFloat(gray)
            }

            val output = Array(1) { FloatArray(FONT_LABELS.size) }
            interp.run(input, output)
            val scores = output[0]
            val idx = scores.indices.maxByOrNull { scores[it] } ?: 0
            return if (scores[idx] > 0.3f && idx < FONT_LABELS.size) FONT_LABELS[idx] else null
        } catch (_: Exception) { return null }
    }

    private fun loadModel(path: String): MappedByteBuffer {
        val fd = TextSwapApp.instance.assets.openFd(path)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length)
    }

    // ── Fallback ──

    private fun sampleTextColor(bmp: Bitmap, rect: Rect): Int {
        val counts = mutableMapOf<Int, Int>()
        val sx = maxOf(1, rect.width() / 8); val sy = maxOf(1, rect.height() / 4)
        for (y in rect.top until rect.bottom step sy)
            for (x in rect.left until rect.right step sx) {
                if (x >= bmp.width || y >= bmp.height) continue
                val q = quantize(bmp.getPixel(x, y))
                counts[q] = (counts[q] ?: 0) + 1
            }
        return counts.maxByOrNull { it.value }?.key ?: Color.BLACK
    }

    private fun quantize(c: Int): Int {
        val r = (Color.red(c) / 32) * 32; val g = (Color.green(c) / 32) * 32
        val b = (Color.blue(c) / 32) * 32
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    private fun detectShadow(bmp: Bitmap, rect: Rect, _tc: Int): Int {
        val off = (rect.height() * 0.15f).roundToInt().coerceAtLeast(2)
        val sy = rect.bottom + off; val sx = rect.centerX()
        if (sy >= bmp.height || sy < 0) return 0x00000000
        val p = bmp.getPixel(sx, sy)
        return if ((Color.red(p) + Color.green(p) + Color.blue(p)) / 3 < 128) p else 0x00000000
    }

    fun close() { interpreter?.close(); interpreter = null }
}