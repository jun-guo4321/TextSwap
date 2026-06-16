package com.textswap.app.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.textswap.app.TextSwapApp
import com.textswap.app.model.BatchStatus
import com.textswap.app.model.ImageTask
import com.textswap.app.model.ReplaceTask
import com.textswap.app.model.TaskStatus
import com.textswap.app.service.ImageProcessor
import com.textswap.app.service.OcrService
import com.textswap.app.util.ImageExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class MainUiState(
    val imageTasks: List<ImageTask> = emptyList(),
    val batchStatus: BatchStatus = BatchStatus.PENDING,
    val uniqueTexts: List<String> = emptyList(),
    val selectedText: String? = null,
    val targetText: String = "",
    val progress: Int = 0,
    val progressTotal: Int = 0,
    val previewBitmap: Bitmap? = null,
    val error: String? = null,
    val exportDone: Boolean = false,
    val exportedCount: Int = 0
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun addImages(uris: List<Uri>) {
        val newTasks = uris.map { uri -> ImageTask(id = UUID.randomUUID().toString(), uri = uri) }
        _uiState.value = _uiState.value.copy(
            imageTasks = _uiState.value.imageTasks + newTasks,
            batchStatus = BatchStatus.PENDING, error = null)
    }

    fun removeImage(taskId: String) {
        _uiState.value = _uiState.value.copy(
            imageTasks = _uiState.value.imageTasks.filter { it.id != taskId })
    }

    fun clearImages() { _uiState.value = MainUiState() }

    fun startOcr() {
        val tasks = _uiState.value.imageTasks
        if (tasks.isEmpty()) return
        _uiState.value = _uiState.value.copy(batchStatus = BatchStatus.OCR_RUNNING, error = null)
        viewModelScope.launch {
            try {
                val results = ImageProcessor.batchOcr(tasks)
                val allTexts = results.flatMap { it.textRegions }.map { it.text }.distinct().sorted()
                _uiState.value = _uiState.value.copy(
                    imageTasks = results, batchStatus = BatchStatus.OCR_DONE, uniqueTexts = allTexts)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(batchStatus = BatchStatus.PENDING, error = "OCR失败: ${e.message}")
            }
        }
    }

    fun selectText(text: String) { _uiState.value = _uiState.value.copy(selectedText = text) }
    fun setTargetText(text: String) { _uiState.value = _uiState.value.copy(targetText = text) }

    fun generatePreview() {
        val state = _uiState.value
        val sel = state.selectedText ?: return
        val tgt = state.targetText; if (tgt.isBlank()) return
        val tasks = state.imageTasks; if (tasks.isEmpty()) return
        viewModelScope.launch {
            try {
                val first = tasks.first()
                val regions = first.textRegions.filter { it.text == sel }
                if (regions.isEmpty()) { _uiState.value = _uiState.value.copy(error = "未找到匹配区域"); return@launch }
                val bmp = loadBitmap(first.uri)
                val r = com.textswap.app.service.FontMatcher.analyzeRegion(bmp, regions.first())
                val e = com.textswap.app.service.InpaintService.eraseText(bmp, r)
                val rendered = com.textswap.app.service.TextRenderer.render(e, r, tgt)
                _uiState.value = _uiState.value.copy(previewBitmap = rendered, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "预览失败: ${e.message}")
            }
        }
    }

    fun startBatchReplace() {
        val state = _uiState.value
        val sel = state.selectedText ?: return
        val tgt = state.targetText; if (tgt.isBlank()) return
        val tasks = state.imageTasks; if (tasks.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            batchStatus = BatchStatus.REPLACING, progress = 0, progressTotal = tasks.size, error = null)

        val replaceTasks = tasks.mapNotNull { task ->
            val regions = task.textRegions.filter { it.text == sel }
            if (regions.isEmpty()) null else ReplaceTask(sourceText = sel, targetText = tgt, regions = regions)
        }
        viewModelScope.launch {
            try {
                val results = ImageProcessor.batchReplace(tasks, replaceTasks) { cur, tot ->
                    _uiState.value = _uiState.value.copy(progress = cur, progressTotal = tot)
                }
                val ok = results.count { it.status == TaskStatus.DONE }
                val fail = results.count { it.status == TaskStatus.FAILED }
                _uiState.value = _uiState.value.copy(
                    imageTasks = results, batchStatus = BatchStatus.DONE, progress = tasks.size,
                    error = if (fail > 0) "${fail}张处理失败" else null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(batchStatus = BatchStatus.PENDING, error = "替换失败: ${e.message}")
            }
        }
    }

    fun exportToGallery() {
        viewModelScope.launch {
            try {
                val done = _uiState.value.imageTasks.filter { it.status == TaskStatus.DONE && it.resultCachePath != null }
                val pairs = done.mapNotNull { task ->
                    val bmp = ImageProcessor.getCachedBitmap(task.id)
                        ?: BitmapFactory.decodeFile(task.resultCachePath)
                    if (bmp != null) Pair("TextSwap_${task.id.take(8)}.png", bmp) else null
                }
                if (pairs.isEmpty()) {
                    _uiState.value = _uiState.value.copy(error = "没有可导出的图片")
                    return@launch
                }
                val uris = ImageExporter.saveToGallery(pairs)
                _uiState.value = _uiState.value.copy(
                    exportDone = true, exportedCount = uris.size,
                    error = if (uris.size < pairs.size) "部分导出失败" else null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "导出失败: ${e.message}")
            }
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap {
        val resolver = TextSwapApp.instance.contentResolver
        return BitmapFactory.decodeStream(resolver.openInputStream(uri), null,
            BitmapFactory.Options().apply { inSampleSize = 2 })
            ?: throw IllegalStateException("Failed to load bitmap")
    }

    override fun onCleared() {
        super.onCleared()
        OcrService.close()
    }
}