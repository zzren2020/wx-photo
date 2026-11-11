package com.articleimages.downloader.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.articleimages.downloader.App
import com.articleimages.downloader.core.Article
import com.articleimages.downloader.core.ArticleParser
import com.articleimages.downloader.core.ArticleUrl
import com.articleimages.downloader.core.DownloadItem
import com.articleimages.downloader.core.SafeName
import com.articleimages.downloader.data.AppLog
import com.articleimages.downloader.data.DownloadController
import com.articleimages.downloader.data.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class UiImage(
    val number: Int,
    val url: String,
    val selected: Boolean = true,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = Settings(application)
    private val repository = (application as App).repository

    var urlText by mutableStateOf("")
    var article by mutableStateOf<Article?>(null)
        private set

    var entries by mutableStateOf<List<UiImage>>(emptyList())
        private set

    var reading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var toast by mutableStateOf<String?>(null)

    var preferOriginal by mutableStateOf(settings.preferOriginal)
        private set

    var fallbackUrl by mutableStateOf<String?>(null)

    val downloadState: StateFlow<DownloadController.State> = DownloadController.state

    fun onSharedText(text: String) {
        urlText = text.trim()
        AppLog.i("入口", "已填入分享链接")
    }

    /** 更新开关并持久化；方法名刻意不用 setPreferOriginal，避免与属性 setter 的 JVM 签名冲突。 */
    fun updatePreferOriginal(value: Boolean) {
        preferOriginal = value
        settings.preferOriginal = value
    }

    fun read(coverOnly: Boolean) {
        if (reading) return
        reading = true
        errorMessage = null
        article = null
        entries = emptyList()
        viewModelScope.launch {
            try {
                val url = ArticleUrl.extract(urlText)
                AppLog.i("读取", "规范化链接：$url")
                val result = repository.fetchArticle(url, coverOnly)
                applyArticle(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "读取失败"
                AppLog.e("读取", "失败", e)
            } finally {
                reading = false
            }
        }
    }

    fun toggle(number: Int) {
        entries = entries.map {
            if (it.number == number) it.copy(selected = !it.selected) else it
        }
    }

    fun selectAll() {
        entries = entries.map { it.copy(selected = true) }
    }

    fun invertSelection() {
        entries = entries.map { it.copy(selected = !it.selected) }
    }

    fun downloadSelected() {
        val current = article ?: return
        val selected = entries.filter { it.selected }
        if (selected.isEmpty()) {
            toast = "请先勾选图片"
            return
        }
        val folder = buildFolderName(current.title)
        val accepted = DownloadController.start(
            getApplication<Application>(),
            DownloadController.Job(
                article = current,
                items = selected.map { DownloadItem(it.number, it.url) },
                preferOriginal = preferOriginal,
                folderName = folder,
            ),
        )
        if (accepted) {
            AppLog.i("下载", "提交 ${selected.size} 张，目录 $folder")
        } else {
            toast = "已有下载任务在进行中"
        }
    }

    fun startFallback() {
        try {
            fallbackUrl = ArticleUrl.extract(urlText)
            errorMessage = null
            AppLog.i("兜底", "打开内置浏览器：$fallbackUrl")
        } catch (e: Exception) {
            toast = e.message ?: "链接无效"
            AppLog.w("兜底", "链接无效：${e.message}")
        }
    }

    fun applyExtracted(title: String, rawImages: List<String>) {
        val seen = LinkedHashSet<String>()
        rawImages.forEach { raw ->
            ArticleUrl.normalizeImage(raw)?.let { seen.add(it) }
        }
        if (seen.isEmpty()) {
            toast = "未提取到图片，请确认页面已正常显示文章"
            AppLog.w("兜底", "WebView 未提取到图片")
            return
        }
        val resolvedTitle = title.trim().ifEmpty { ArticleParser.DEFAULT_TITLE }
        val parsed = Article(
            title = resolvedTitle,
            url = fallbackUrl ?: urlText,
            images = seen.toList(),
        )
        article = parsed
        entries = parsed.images.mapIndexed { index, url -> UiImage(index + 1, url) }
        fallbackUrl = null
        errorMessage = null
        AppLog.i("兜底", "WebView 提取成功：$resolvedTitle，共 ${parsed.images.size} 张")
    }

    fun clearError() {
        errorMessage = null
    }

    private fun applyArticle(result: Article) {
        article = result
        entries = result.images.mapIndexed { index, url -> UiImage(index + 1, url) }
        AppLog.i("读取", "标题：${result.title}；图片 ${result.images.size} 张")
        if (result.images.isEmpty()) {
            toast = "正文中没有找到在线图片"
        }
    }

    private fun buildFolderName(title: String): String {
        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val id = UUID.randomUUID().toString().replace("-", "").take(4)
        return "${time}_${SafeName.of(title)}_$id"
    }
}
