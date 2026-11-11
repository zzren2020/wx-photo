package com.articleimages.downloader.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

object MimeTypes {
    fun of(extension: String): String = when (extension.lowercase()) {
        ".png" -> "image/png"
        ".jpg", ".jpeg" -> "image/jpeg"
        ".gif" -> "image/gif"
        ".webp" -> "image/webp"
        ".avif" -> "image/avif"
        ".bmp" -> "image/bmp"
        else -> "image/*"
    }
}

/**
 * 落盘策略：
 *  - 默认写入系统相册：Pictures/公众号图片/<文章目录>/001.jpg，MediaStore 管理，无需存储权限；
 *  - 设置里可选 SAF 自定义目录（DocumentFile），用于保存到 SD 卡等位置。
 */
class ImageSaver(private val context: Context) {

    fun save(image: DownloadedImage, folder: String, displayName: String): String {
        val settings = Settings(context)
        return if (settings.useCustomDir) {
            val treeUri = settings.treeUri?.let(Uri::parse)
                ?: throw IOException("未选择自定义目录，请在设置中选择。")
            saveToTree(treeUri, folder, displayName, image)
        } else {
            saveToMediaStore(DEFAULT_RELATIVE_ROOT + "/" + folder, displayName, image)
        }
    }

    private fun saveToMediaStore(
        relativePath: String,
        displayName: String,
        image: DownloadedImage,
    ): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, MimeTypes.of(image.extension))
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: throw IOException("无法写入相册")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                image.file.inputStream().use { input -> input.copyTo(output, COPY_BUFFER) }
            } ?: throw IOException("无法打开相册输出流")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri.toString()
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun saveToTree(
        treeUri: Uri,
        folder: String,
        displayName: String,
        image: DownloadedImage,
    ): String {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("无法访问所选目录")
        val dir = root.findFile(folder) ?: root.createDirectory(folder)
            ?: throw IOException("无法创建子目录")
        var target = dir.findFile(displayName)
        if (target != null) {
            // 同一目录重复下载时避免覆盖：追加 -1、-2…
            val base = displayName.substringBeforeLast('.')
            val suffix = displayName.substringAfterLast('.', "")
            var counter = 1
            while (target != null) {
                val name = if (suffix.isEmpty()) "$base-$counter" else "$base-$counter.$suffix"
                target = dir.findFile(name)
                if (target == null) {
                    return createAndWrite(dir, name, image)
                }
                counter++
            }
        }
        val document = dir.createFile(MimeTypes.of(image.extension), displayName)
            ?: throw IOException("无法创建文件")
        try {
            context.contentResolver.openOutputStream(document.uri)?.use { output ->
                image.file.inputStream().use { input -> input.copyTo(output, COPY_BUFFER) }
            } ?: throw IOException("无法打开输出流")
            return document.uri.toString()
        } catch (e: Exception) {
            runCatching { document.delete() }
            throw e
        }
    }

    private fun createAndWrite(dir: DocumentFile, displayName: String, image: DownloadedImage): String {
        val document = dir.createFile(MimeTypes.of(image.extension), displayName)
            ?: throw IOException("无法创建文件")
        try {
            context.contentResolver.openOutputStream(document.uri)?.use { output ->
                image.file.inputStream().use { input -> input.copyTo(output, COPY_BUFFER) }
            } ?: throw IOException("无法打开输出流")
            return document.uri.toString()
        } catch (e: Exception) {
            runCatching { document.delete() }
            throw e
        }
    }

    private companion object {
        const val DEFAULT_RELATIVE_ROOT = "Pictures/公众号图片"
        const val COPY_BUFFER = 64 * 1024
    }
}
