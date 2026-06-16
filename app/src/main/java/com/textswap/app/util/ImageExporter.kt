package com.textswap.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.textswap.app.TextSwapApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 图片导出工具：将处理后的 Bitmap 保存到相册或临时目录
 */
object ImageExporter {

    /**
     * 批量保存到系统相册（Pictures/TextSwap/）
     * @return 保存成功的文件路径列表
     */
    suspend fun saveToGallery(
        bitmaps: List<Pair<String, Bitmap>>, // (文件名, Bitmap)
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100
    ): List<Uri> = withContext(Dispatchers.IO) {
        val context = TextSwapApp.instance
        bitmaps.mapNotNull { (fileName, bitmap) ->
            saveToMediaStore(context, bitmap, fileName, format, quality)
        }
    }

    /**
     * 保存到应用私有临时目录（供预览用）
     */
    suspend fun saveToTemp(
        bitmap: Bitmap,
        fileName: String
    ): File = withContext(Dispatchers.IO) {
        val dir = File(TextSwapApp.instance.cacheDir, "preview")
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file
    }

    private fun saveToMediaStore(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        format: Bitmap.CompressFormat,
        quality: Int
    ): Uri? {
        val mimeType = when (format) {
            Bitmap.CompressFormat.PNG -> "image/png"
            Bitmap.CompressFormat.JPEG -> "image/jpeg"
            else -> "image/png"
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TextSwap")
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ) ?: return null

        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(format, quality, out)
        }

        return uri
    }
}