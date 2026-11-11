package com.articleimages.downloader.core

/** 一篇文章的解析结果。images 已做 URL 规范化、原图改写与去重。 */
data class Article(
    val title: String,
    val url: String,
    val images: List<String>,
)

/** 下载任务中的一张图片。number 为文章中的序号（从 1 开始）。 */
data class DownloadItem(
    val number: Int,
    val url: String,
)

class ArticleUrlException(message: String) : Exception(message)

class ArticleParseException(message: String) : Exception(message)

class ImageFormatException(message: String) : Exception(message)
