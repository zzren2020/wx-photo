package com.articleimages.downloader.data

import com.articleimages.downloader.core.Article
import com.articleimages.downloader.core.ArticleParser
import com.articleimages.downloader.core.ArticleUrl
import com.articleimages.downloader.core.ImageSniffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DownloadedImage(
    val file: File,
    val extension: String,
    val size: Long,
)

class ArticleRepository(
    private val client: OkHttpClient,
    private val workingDir: File,
) {

    suspend fun fetchArticle(url: String, coverOnly: Boolean): Article = withContext(Dispatchers.IO) {
        AppLog.i("网络", "GET 文章 $url（coverOnly=$coverOnly）")
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
        CookieStore.cookie?.let { builder.header("Cookie", it) }

        client.newCall(builder.build()).await().use { response ->
            val finalUrl = response.request.url
            AppLog.d("网络", "最终地址 $finalUrl")
            if (!finalUrl.host.equals("mp.weixin.qq.com", true)) {
                throw IOException("链接跳转到了其他网站，请检查文章链接。")
            }
            if (!response.isSuccessful) {
                throw IOException("文章请求失败：HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("文章响应为空")
            val declared = body.contentLength()
            if (declared > MAX_HTML) throw IOException("文件过大，已跳过。")
            val bytes = readLimited(body.byteStream(), MAX_HTML)
            val html = String(bytes, Charsets.UTF_8)
            AppLog.d("解析", "HTML ${bytes.size} 字节")
            if (coverOnly) ArticleParser.parseCover(html, url) else ArticleParser.parse(html, url)
        }
    }

    /**
     * 下载单张图片：勾选“原图”时先试 Original(url)，失败自动回退缩略图地址。
     * 采用临时文件流式落盘，避免 80MB 图片直接进内存。
     */
    suspend fun downloadImage(
        url: String,
        referer: String,
        original: Boolean,
        onBytes: (Long) -> Unit = {},
    ): DownloadedImage = withContext(Dispatchers.IO) {
        val preferred = if (original) ArticleUrl.original(url) else url
        try {
            fetchImage(preferred, referer, onBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (preferred == url) throw e
            AppLog.w("下载", "原图失败，回退缩略图：${e.message}")
            fetchImage(url, referer, onBytes)
        }
    }

    private suspend fun fetchImage(
        url: String,
        referer: String,
        onBytes: (Long) -> Unit,
    ): DownloadedImage {
        AppLog.d("下载", "GET 图片 $url")
        val builder = Request.Builder()
            .url(url)
            .header("Referer", referer.ifBlank { "https://mp.weixin.qq.com/" })
            .header("User-Agent", UA)
        CookieStore.cookie?.let { builder.header("Cookie", it) }

        client.newCall(builder.build()).await().use { response ->
            if (!response.isSuccessful) throw IOException("图片请求失败：HTTP ${response.code}")
            val body = response.body ?: throw IOException("图片响应为空")
            val declared = body.contentLength()
            if (declared > MAX_IMAGE) throw IOException("文件过大，已跳过。")

            val temp = File.createTempFile("wximg_", ".part", workingDir)
            var total = 0L
            val header = ByteArray(16)
            var headerLength = 0
            try {
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_IMAGE) throw IOException("文件过大，已跳过。")
                            if (headerLength < header.size) {
                                val copy = minOf(header.size - headerLength, read)
                                System.arraycopy(buffer, 0, header, headerLength, copy)
                                headerLength += copy
                            }
                            output.write(buffer, 0, read)
                            onBytes(total)
                        }
                    }
                }
                val extension = ImageSniffer.sniff(header.copyOf(headerLength))
                AppLog.d("下载", "图片完成：${total} 字节，$extension")
                DownloadedImage(file = temp, extension = extension, size = total)
            } catch (e: Exception) {
                temp.delete()
                throw e
            }
        }
    }

    private suspend fun readLimited(input: InputStream, limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IOException("文件过大，已跳过。")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response)
                } else {
                    response.close()
                }
            }
        })
    }

    companion object {
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        const val MAX_HTML = 12L * 1024 * 1024
        const val MAX_IMAGE = 80L * 1024 * 1024
        private const val BUFFER_SIZE = 64 * 1024
    }
}
