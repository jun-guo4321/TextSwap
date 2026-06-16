package com.textswap.app.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import com.textswap.app.model.TextRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文字渲染服务
 *
 * 在原图指定位置绘制新文字，尽量匹配原文字的字体、颜色、阴影等属性
 */
object TextRenderer {

    /**
     * 在图片上渲染新文字到指定区域
     */
    suspend fun render(
        bitmap: Bitmap,
        region: TextRegion,
        newText: String
    ): Bitmap = withContext(Dispatchers.Default) {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val rect = region.boundingBox

        // 计算适合区域的文字大小
        val textSize = calculateFittingTextSize(newText, rect.width(), rect.height(), region.fontSize)

        // 绘制阴影（如果有）
        if (region.shadowColor != 0x00000000) {
            drawShadow(canvas, newText, rect, textSize, region.shadowColor)
        }

        // 绘制主文字
        drawText(canvas, newText, rect, textSize, region.textColor, region.fontName)

        result
    }

    /**
     * 计算让文字尽可能填满区域且不超出的字号
     */
    private fun calculateFittingTextSize(
        text: String,
        maxWidth: Int,
        maxHeight: Int,
        originalSize: Float
    ): Float {
        if (originalSize <= 0f) return maxHeight * 0.7f

        val paint = TextPaint().apply {
            textSize = originalSize
            isAntiAlias = true
        }

        val textWidth = paint.measureText(text)

        // 如果原字号能放下，就用原字号；否则等比缩小
        return if (textWidth <= maxWidth * 1.1f) {
            originalSize
        } else {
            originalSize * (maxWidth / textWidth) * 0.95f
        }
    }

    /**
     * 绘制阴影（偏移1-2像素的右下方向）
     */
    private fun drawShadow(
        canvas: Canvas,
        text: String,
        rect: android.graphics.Rect,
        textSize: Float,
        shadowColor: Int
    ) {
        val paint = TextPaint().apply {
            color = shadowColor
            this.textSize = textSize
            isAntiAlias = true
            typeface = Typeface.DEFAULT
        }

        val offset = textSize * 0.04f
        val x = rect.left.toFloat() + offset
        val y = rect.bottom.toFloat() - paint.descent() + offset

        canvas.drawText(text, x, y, paint)
    }

    /**
     * 绘制主文字（垂直居中、左对齐）
     */
    private fun drawText(
        canvas: Canvas,
        text: String,
        rect: android.graphics.Rect,
        textSize: Float,
        textColor: Int,
        fontName: String
    ) {
        val paint = TextPaint().apply {
            color = textColor
            this.textSize = textSize
            isAntiAlias = true
            typeface = resolveTypeface(fontName)
        }

        // 垂直居中：基线 = 区域中心 + (字体度量中心到基线的偏移)
        val fm = paint.fontMetrics
        val textCenterY = rect.exactCenterY()
        val baselineY = textCenterY - (fm.ascent + fm.descent) / 2f

        canvas.drawText(text, rect.left.toFloat(), baselineY, paint)
    }

    /**
     * 将字体名字符串映射为 Android Typeface。
     * 升级路径：可在此处加载 asset 中的自定义字体文件
     */
    private fun resolveTypeface(fontName: String): Typeface {
        return when (fontName.lowercase()) {
            "pingfang sc", "pingfangsc" -> Typeface.DEFAULT
            "helvetica", "helvetica neue" -> Typeface.DEFAULT
            "roboto" -> Typeface.DEFAULT
            "sf pro", "sf pro display" -> Typeface.DEFAULT
            else -> Typeface.DEFAULT
        }
    }
}