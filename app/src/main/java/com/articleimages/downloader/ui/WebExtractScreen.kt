package com.articleimages.downloader.ui

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.articleimages.downloader.data.AppLog
import com.articleimages.downloader.data.CookieStore
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 兜底通道：遇到“需要验证/正文读不到”时，用内置浏览器打开文章。
 * 用户手动完成验证后，注入 JS 抓取 #js_content 内的图片，并把 Cookie 交给下载器。
 */
@Composable
fun WebExtractScreen(
    url: String,
    onExtracted: (String, List<String>) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("正在加载文章…若出现验证，请完成后点“重新提取”") }
    var pendingResult by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    val webViewHolder = remember { arrayOfNulls<WebView>(1) }
    val currentOnExtracted by rememberUpdatedState(onExtracted)

    LaunchedEffect(pendingResult) {
        val result = pendingResult
        if (result != null) {
            pendingResult = null
            currentOnExtracted(result.first, result.second)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewHolder[0]?.let { view ->
                view.stopLoading()
                view.destroy()
            }
            webViewHolder[0] = null
        }
    }

    fun processRawResult(raw: String?) {
        if (raw.isNullOrBlank() || raw == "null") {
            loading = false
            message = "页面尚未就绪，可稍后点“重新提取”"
            return
        }
        try {
            val unwrapped = if (raw.trimStart().startsWith("\"")) {
                JSONTokener(raw).nextValue() as? String ?: raw
            } else {
                raw
            }
            val json = JSONObject(unwrapped)
            val title = json.optString("title")
            val cover = json.optString("cover")
            val array = json.optJSONArray("images")
            val images = ArrayList<String>()
            if (array != null) {
                for (index in 0 until array.length()) {
                    val value = array.optString(index)
                    if (value.isNotBlank()) images.add(value)
                }
            }
            if (images.isEmpty() && cover.isNotBlank()) images.add(cover)
            loading = false
            if (images.isEmpty()) {
                message = "未提取到正文图片，请确认页面已正常显示文章"
                AppLog.w("兜底", "JS 未提取到图片")
            } else {
                message = "提取到 ${images.size} 张，正在返回…"
                pendingResult = title to images
            }
        } catch (e: Exception) {
            loading = false
            message = "解析提取结果失败：${e.message}"
            AppLog.e("兜底", "解析 JS 结果失败", e)
        }
    }

    fun extractFrom(view: WebView) {
        loading = true
        view.evaluateJavascript(EXTRACT_JS) { raw ->
            processRawResult(raw)
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "内置浏览器提取",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    val view = webViewHolder[0]
                    if (view == null) {
                        message = "页面尚未就绪"
                    } else {
                        message = "正在重新提取…"
                        extractFrom(view)
                    }
                }
            ) {
                Text("重新提取")
            }
            TextButton(
                onClick = {
                    loading = true
                    message = "正在刷新页面…"
                    webViewHolder[0]?.reload()
                }
            ) {
                Text("刷新")
            }
        }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadsImagesAutomatically = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, pageUrl: String?) {
                            super.onPageFinished(view, pageUrl)
                            view.postDelayed({
                                if (webViewHolder[0] != null) {
                                    val cookie = pageUrl?.let { CookieManager.getInstance().getCookie(it) }
                                    if (!cookie.isNullOrBlank()) {
                                        CookieStore.cookie = cookie
                                        AppLog.d("兜底", "已同步 WebView Cookie")
                                    }
                                    extractFrom(view)
                                }
                            }, 1200)
                        }
                    }
                    loadUrl(url)
                }.also { webViewHolder[0] = it }
            },
        )
    }
}

private const val EXTRACT_JS = """
(function () {
  var result = { title: '', cover: '', images: [] };
  var t = document.querySelector('meta[property="og:title"]') || document.querySelector('meta[name="og:title"]');
  if (t) result.title = t.getAttribute('content') || '';
  var c = document.querySelector('meta[property="og:image"]') || document.querySelector('meta[name="og:image"]');
  if (c) result.cover = c.getAttribute('content') || '';
  var body = document.getElementById('js_content');
  var imgs = body ? body.querySelectorAll('img') : [];
  for (var i = 0; i < imgs.length; i++) {
    var el = imgs[i];
    var s = el.getAttribute('data-src') || el.getAttribute('data-original') || el.getAttribute('src') || '';
    if (s) result.images.push(s);
  }
  return JSON.stringify(result);
})()
"""