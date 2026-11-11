package com.articleimages.downloader.core

import org.jsoup.parser.Parser
import java.net.URI

/**
 * 与桌面版 Engine.ArticleUrl / Original 等价：
 *  - 从任意文本里提取第一个 mp.weixin.qq.com/s 链接；
 *  - 校验 host、端口、userinfo、路径；
 *  - 强制 https、去掉 fragment；
 *  - qpic 缩略图尺寸改写为 /0（原图）。
 */
object ArticleUrl {

    private val LINK = Regex("""https?://mp\.weixin\.qq\.com/[^\s<>"“”]+""")
    private val SIZE_IN_PATH = Regex("""/(?:640|1080|720|300|1000)(?=\?|$)""")

    fun extract(input: String?): String {
        val text = input ?: ""
        val match = LINK.find(text)
            ?: throw ArticleUrlException("请粘贴 https://mp.weixin.qq.com/ 开头的公众号文章链接。")
        val decoded = Parser.unescapeEntities(match.value, false)
        val uri = try {
            URI(decoded)
        } catch (e: Exception) {
            null
        } ?: throw ArticleUrlException("请粘贴 https://mp.weixin.qq.com/ 开头的公众号文章链接。")

        val host = uri.host
        val portOk = uri.port == -1 || uri.port == 443
        if (host == null || !host.equals("mp.weixin.qq.com", true) || !portOk || !uri.userInfo.isNullOrEmpty()) {
            throw ArticleUrlException("请粘贴 https://mp.weixin.qq.com/ 开头的公众号文章链接。")
        }

        val path = uri.path ?: ""
        val isArticlePath = path == "/s" || path.startsWith("/s/")
        if (!isArticlePath) {
            throw ArticleUrlException("请使用公众号文章链接（/s 或 /s/ 开头），不是公众号主页。")
        }

        var out = decoded
        val hash = out.indexOf('#')
        if (hash >= 0) out = out.substring(0, hash)
        if (out.startsWith("http://", true)) {
            out = "https://" + out.substring("http://".length)
        }
        return out
    }

    /** 仅对 qpic.cn / *.qpic.cn 把 /640、/1080、/720、/300、/1000 改成 /0。 */
    fun original(url: String): String {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return url
        }
        val host = uri.host ?: return url
        if (!host.equals("qpic.cn", true) && !host.endsWith(".qpic.cn", true)) return url
        return SIZE_IN_PATH.replace(url, "/0")
    }

    /**
     * 图片 URL 规范化：
     * data: 丢弃；// 补 https；只接受 http/https；去 fragment；http 升级为 https；再做原图改写。
     * 返回 null 表示该 URL 不可用。
     */
    fun normalizeImage(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty() || s.startsWith("data:", true)) return null
        if (s.startsWith("//")) s = "https:$s"

        val uri = try {
            URI(s)
        } catch (e: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null

        val hash = s.indexOf('#')
        if (hash >= 0) s = s.substring(0, hash)
        if (s.startsWith("http://", true)) {
            s = "https://" + s.substring("http://".length)
        }
        return original(s)
    }
}
