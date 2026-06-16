package com.textswap.app.model

import android.graphics.Rect
import android.net.Uri

data class ImageTask(
    val id: String,
    val uri: Uri,
    val status: TaskStatus = TaskStatus.PENDING,
    val textRegions: List<TextRegion> = emptyList(),
    val errorMessage: String? = null,
    val resultCachePath: String? = null
)

data class TextRegion(
    val id: String,
    val text: String,
    val boundingBox: Rect,
    val fontSize: Float = 0f,
    val fontName: String = "PingFang SC",
    val textColor: Int = 0xFF000000.toInt(),
    val shadowColor: Int = 0x00000000,
    val rotation: Float = 0f,
    val confidence: Float = 0f
)

data class ReplaceTask(
    val sourceText: String,
    val targetText: String,
    val regions: List<TextRegion>
)

enum class BatchStatus {
    PENDING, OCR_RUNNING, OCR_DONE, REPLACING, DONE, CANCELLED
}

enum class TaskStatus {
    PENDING, OCR_RUNNING, OCR_FAILED, OCR_DONE, REPLACING, DONE, FAILED
}