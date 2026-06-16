package com.textswap.app.service

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.textswap.app.model.TextRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume

/**
 * OCR 文字识别服务
 * 使用 ML Kit Chinese Text Recognition V2，完全离线
 */
object OcrService {

    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    /**
     * 识别单张图片中的文字区域
     * @return 按左上角y坐标排序的文字区域列表
     */
    suspend fun recognize(bitmap: Bitmap): Result<List<TextRegion>> =
        withContext(Dispatchers.Default) {
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val result = suspendCancellableCoroutine { cont ->
                    recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            val regions = visionText.textBlocks.flatMap { block ->
                                block.lines.flatMap { line ->
                                    line.elements.map { element ->
                                        TextRegion(
                                            id = UUID.randomUUID().toString(),
                                            text = element.text.trim(),
                                            boundingBox = element.boundingBox?.let { box ->
                                                Rect(box.left, box.top, box.right, box.bottom)
                                            } ?: Rect(0, 0, 0, 0),
                                            confidence = element.confidence ?: 0f
                                        )
                                    }
                                }
                            }.sortedBy { it.boundingBox.top }
                            cont.resume(Result.success(regions))
                        }
                        .addOnFailureListener { e ->
                            cont.resume(Result.failure(e))
                        }
                }
                result
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun close() {
        recognizer.close()
    }
}