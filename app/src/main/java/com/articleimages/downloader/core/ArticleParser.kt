package com.articleimages.downloader.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI

/**
 * 与桌面版 Engine.Parse / ParseCover 等价，但使用 Jsoup 而非正则定位正文：
 *  - 正文容器：div#js_content（兜底任意 id=js_content）；
 *  - 标题：og:title（property 或 name），兜底 <title>；
 *  - 图片：data-src > data-original > src，去 data:、补协议、去重、原图改写；
 *  - 封面：og:image，兜底 msg_cdn_url / cdn_url 变量（含 JS 反转义）。
 */
object ArticleParser {

    const val DEFAULT_TITLE = "公众号文章"
    private const val DEFAULT_COVER_TITLE = "公众号文章封面"

    private val COVER_VAR = Regex("""\b(?:var\s+)?(?:msg_cdn_url|cdn_url)\s*=\s*(?:"([^"]+)"|'([^']+)')""")
    private val JS_ESCAPE = Regex("""\\u([0-9a-fA-F]{4})|\\x([0-9a-fA-F]{2})""")

    fun parse(html: String, url: String): Article {
        val doc = Jsoup.parse(html)
        val body = doc.selectFirst("div#js_content")
            ?: doc.selectFirst("[id=js_content]")
            ?: throw ArticleParseException(
                "没有读到文章正文。文章可能需要在微信中验证、已删除或限制访问。请在浏览器中打开确认，再重试读取。"
            )

        var title = DEFAULT_TITLE
        val metaTitle = doc.select("meta").firstOrNull { meta ->
            meta.attr("property").equals("og:title", true) || meta.attr("name").equals("og:title", true)
        }
        metaTitle?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }?.let { title = it }

        if (title == DEFAULT_TITLE) {
            doc.selectFirst("title")?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { title = it }
        }

        val seen = LinkedHashSet<String>()
        body.select("img").forEach { img ->
            val raw = firstNonBlank(img, "data-src", "data-original", "src") ?: return@forEach
            ArticleUrl.normalizeImage(raw)?.let { seen.add(it) }
        }

        return Article(title = title, url = url, images = seen.toList())
    }

    fun parseCover(html: String, url: String): Article {
        val doc = Jsoup.parse(html)
        var title = DEFAULT_COVER_TITLE
        var cover: String? = null

        doc.select("meta").forEach { meta ->
            val key = meta.attr("property").ifBlank { meta.attr("name") }
            val content = meta.attr("content")
            if (content.isBlank()) return@forEach
            if (key.equals("og:title", true)) title = content.trim() + " · 封面"
            if (key.equals("og:image", true) && cover.isNullOrBlank()) cover = content
        }

        if (cover.isNullOrBlank()) {
            val match = COVER_VAR.find(html)
            if (match != null) {
                cover = match.groupValues[1].ifEmpty { match.groupValues[2] }
            }
        }

        val normalized: String = cover?.takeIf { it.isNotBlank() }?.let { raw ->
            var s = JS_ESCAPE.replace(raw) { m ->
                val hex = m.groupValues[1].ifEmpty { m.groupValues[2] }
                hex.toInt(16).toChar().toString()
            }
            s = s.replace("\\/", "/")
            s = Parser.unescapeEntities(s, false).trim()
            if (s.startsWith("//")) s = "https:$s"
            if (s.startsWith("http://", true)) s = "https://" + s.substring("http://".length)
            val uri = try {
                URI(s)
            } catch (e: Exception) {
                null
            }
            val scheme = uri?.scheme?.lowercase()
            if (uri == null || (scheme != "https" && scheme != "http")) null else uri.toString()
        } ?: throw ArticleParseException(
            "未找到文章封面。文章可能没有公开封面，或当前页面需要微信验证。请确认文章链接可正常打开。"
        )

        return Article(title = title, url = url, images = listOf(normalized))
    }

    private fun firstNonBlank(element: Element, vararg names: String): String? {
        for (name in names) {
            val value = element.attr(name)
            if (value.isNotBlank()) return value
        }
        return null
    }
}
