package com.articleimages.downloader.data

/**
 * WebView 兜底提取后，把该页面的 Cookie 交给 OkHttp，
 * 让后续图片下载复用用户在 WebView 里完成的验证会话。
 */
object CookieStore {
    @Volatile
    var cookie: String? = null
}
