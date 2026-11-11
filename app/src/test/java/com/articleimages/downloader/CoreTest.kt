package com.articleimages.downloader

import com.articleimages.downloader.core.ArticleParseException
import com.articleimages.downloader.core.ArticleParser
import com.articleimages.downloader.core.ArticleUrl
import com.articleimages.downloader.core.ArticleUrlException
import com.articleimages.downloader.core.ImageFormatException
import com.articleimages.downloader.core.ImageSniffer
import com.articleimages.downloader.core.SafeName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.function.ThrowingRunnable

/**
 * 与桌面版 --self-test 的 12 条断言对齐，并补充封面、文件名等用例。
 * 纯 JVM 测试，不依赖 Android。
 */
class CoreTest {

    private val sampleHtml = """<title>备用</title><meta property='og:title' content='测试 &amp; 图片'><img src='https://example.com/out.png'><div id='js_content'><div><img data-src='//mmbiz.qpic.cn/mmbiz_png/demo/640?wx_fmt=png&amp;a=1' src='data:image/png;base64,x'></div><img data-src='https://mmbiz.qpic.cn/mmbiz_png/demo/0?wx_fmt=png&amp;a=1'><img src=https://example.com/a.gif><script>var x='<div><img src="https://bad.invalid/x">';</script><!-- <div> --></div><img src='https://example.com/footer.png'>"""

    @Test
    fun titleEntitiesAreDecoded() {
        val article = ArticleParser.parse(sampleHtml, "https://mp.weixin.qq.com/s/demo")
        assertEquals("测试 & 图片", article.title)
    }

    @Test
    fun bodyBoundaryLazyImagesDedupAndScripts() {
        val article = ArticleParser.parse(sampleHtml, "https://mp.weixin.qq.com/s/demo")
        assertEquals(2, article.images.size)
        assertTrue(article.images[0].contains("mmbiz.qpic.cn"))
        assertTrue(article.images[1].contains("example.com/a.gif"))
    }

    @Test
    fun urlEntitiesAreDecoded() {
        val article = ArticleParser.parse(sampleHtml, "https://mp.weixin.qq.com/s/demo")
        assertTrue(article.images[0].contains("&a=1"))
        assertFalse(article.images[0].contains("amp;"))
    }

    @Test
    fun qpicThumbnailIsRewrittenToOriginal() {
        val article = ArticleParser.parse(sampleHtml, "https://mp.weixin.qq.com/s/demo")
        assertTrue(ArticleUrl.original(article.images[0]).contains("/0?"))
    }

    @Test
    fun nonQpicUrlIsUnchanged() {
        val url = "https://example.com/a/640"
        assertEquals(url, ArticleUrl.original(url))
    }

    @Test
    fun articleUrlIsExtractedFromShareText() {
        assertEquals(
            "https://mp.weixin.qq.com/s/demo",
            ArticleUrl.extract("分享 https://mp.weixin.qq.com/s/demo#abc"),
        )
    }

    @Test
    fun nonArticleUrlIsRejected() {
        assertThrows(
            ArticleUrlException::class.java,
            ThrowingRunnable { ArticleUrl.extract("https://example.com/s/a") },
        )
    }

    @Test
    fun challengePageIsRejected() {
        assertThrows(
            ArticleParseException::class.java,
            ThrowingRunnable { ArticleParser.parse("<html>验证页面</html>", "https://mp.weixin.qq.com/s/demo") },
        )
    }

    @Test
    fun pngFormatIsDetected() {
        val header = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
        )
        assertEquals(".png", ImageSniffer.sniff(header))
    }

    @Test
    fun gifFormatIsDetected() {
        assertEquals(".gif", ImageSniffer.sniff("GIF89a".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun webpFormatIsDetected() {
        assertEquals(".webp", ImageSniffer.sniff("RIFF0000WEBP".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun htmlDisguisedAsImageIsRejected() {
        assertThrows(
            ImageFormatException::class.java,
            ThrowingRunnable { ImageSniffer.sniff("<html>error</html>".toByteArray(Charsets.UTF_8)) },
        )
    }

    @Test
    fun httpImageIsUpgradedToHttps() {
        assertEquals(
            "https://mmbiz.qpic.cn/a/0?wx_fmt=png",
            ArticleUrl.normalizeImage("http://mmbiz.qpic.cn/a/640?wx_fmt=png"),
        )
    }

    @Test
    fun ogImageCoverIsParsed() {
        val html = """<meta property="og:image" content="https://mmbiz.qpic.cn/a/640?wx_fmt=jpeg"><meta property="og:title" content="标题">"""
        val article = ArticleParser.parseCover(html, "https://mp.weixin.qq.com/s/x")
        assertEquals("标题 · 封面", article.title)
        assertEquals("https://mmbiz.qpic.cn/a/640?wx_fmt=jpeg", article.images.single())
    }

    @Test
    fun coverVariableWithJsEscapesIsParsed() {
        val html = """<script>var msg_cdn_url = "https:\/\/mmbiz.qpic.cn\/a\/0?wx_fmt=jpeg";</script>"""
        val article = ArticleParser.parseCover(html, "https://mp.weixin.qq.com/s/x")
        assertEquals("https://mmbiz.qpic.cn/a/0?wx_fmt=jpeg", article.images.single())
    }

    @Test
    fun safeNameSanitizesAndTruncates() {
        assertEquals("a_b_c", SafeName.of("a/b:c"))
        assertEquals("公众号文章", SafeName.of("   "))
        assertEquals(65, SafeName.of("x".repeat(100)).length)
    }
}