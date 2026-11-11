package com.articleimages.downloader

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.articleimages.downloader.data.AppLog
import com.articleimages.downloader.data.ArticleRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class App : Application(), ImageLoaderFactory {

    lateinit var repository: ArticleRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
        repository = ArticleRepository(client, cacheDir)
        AppLog.i("App", "启动，版本 ${BuildConfig.VERSION_NAME}")
    }

    /**
     * 统一配置 Coil：
     *  - 微信图片 CDN 需要 Referer，否则可能 403；
     *  - GIF 在 API 28+ 用 ImageDecoder，低版本用 GifDecoder。
     */
    override fun newImageLoader(): ImageLoader {
        val imageClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                if (host == "qpic.cn" || host.endsWith(".qpic.cn")) {
                    chain.proceed(
                        request.newBuilder()
                            .header("Referer", "https://mp.weixin.qq.com/")
                            .header("User-Agent", ArticleRepository.UA)
                            .build()
                    )
                } else {
                    chain.proceed(request)
                }
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageClient)
            .crossfade(true)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}
