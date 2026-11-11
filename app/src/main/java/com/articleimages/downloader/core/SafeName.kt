package com.articleimages.downloader.core

/** 与桌面版 Engine.SafeName 等价：非法字符替换、空白折叠、去尾部点、限长 65、空值回退。 */
object SafeName {

    private val WHITESPACE = Regex("""\s+""")
    private val INVALID = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    fun of(title: String): String {
        var s = title
        for (c in INVALID) s = s.replace(c, '_')
        s = s.map { if (it.code < 32) '_' else it }.joinToString("")
        s = WHITESPACE.replace(s, " ").trim().trimEnd('.')
        if (s.length > 65) s = s.substring(0, 65).trimEnd('.', ' ')
        return s.ifEmpty { "公众号文章" }
    }
}
