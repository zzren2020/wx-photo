package com.articleimages.downloader.core

/** 按字节魔数识别图片格式，返回扩展名；无法识别时抛异常（可识别“HTML 伪装成图片”）。 */
object ImageSniffer {

    fun sniff(header: ByteArray): String {
        if (header.size >= 8 &&
            header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() &&
            header[4] == 0x0D.toByte() && header[5] == 0x0A.toByte() &&
            header[6] == 0x1A.toByte() && header[7] == 0x0A.toByte()
        ) {
            return ".png"
        }
        if (header.size >= 3 &&
            header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()
        ) {
            return ".jpg"
        }
        if (header.size >= 6) {
            val gif = ascii(header, 0, 6)
            if (gif == "GIF87a" || gif == "GIF89a") return ".gif"
        }
        if (header.size >= 12 && ascii(header, 0, 4) == "RIFF" && ascii(header, 8, 4) == "WEBP") {
            return ".webp"
        }
        if (header.size >= 12 && ascii(header, 4, 4) == "ftyp") {
            val brand = ascii(header, 8, 4)
            if (brand == "avif" || brand == "avis") return ".avif"
        }
        if (header.size >= 14 &&
            header[0] == 'B'.code.toByte() && header[1] == 'M'.code.toByte()
        ) {
            return ".bmp"
        }
        throw ImageFormatException("服务器没有返回受支持的图片，可能需要验证或链接已失效。")
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.US_ASCII)
}
