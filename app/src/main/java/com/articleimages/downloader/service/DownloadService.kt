package com.articleimages.downloader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.articleimages.downloader.App
import com.articleimages.downloader.MainActivity
import com.articleimages.downloader.R
import com.articleimages.downloader.data.AppLog
import com.articleimages.downloader.data.DownloadController
import com.articleimages.downloader.data.ImageSaver
import com.articleimages.downloader.data.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.Locale

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                AppLog.i("服务", "收到取消请求")
                DownloadController.cancel()
                if (running == null) stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val spec = DownloadController.takePending()
                // startForegroundService 启动后必须尽快 startForeground，即使马上要结束。
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(0, spec?.items?.size ?: 1),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )

                if (spec == null) {
                    AppLog.w("服务", "没有待执行的下载任务")
                    stopForegroundAndSelf()
                    return START_NOT_STICKY
                }

                val displayFolder = displayFolder(spec.folderName)
                if (DownloadController.cancelRequested) {
                    DownloadController.finish(0, 0, displayFolder, true, null)
                    stopForegroundAndSelf()
                    return START_NOT_STICKY
                }

                val job = scope.launch { runJob(spec, displayFolder) }
                running = job
                DownloadController.attach(job)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runJob(spec: DownloadController.Job, displayFolder: String) {
        val repository = (application as App).repository
        val saver = ImageSaver(this)
        var ok = 0
        var failed = 0
        var sampleUri: String? = null

        try {
            spec.items.forEachIndexed { index, item ->
                if (DownloadController.cancelRequested) throw CancellationException("已取消")
                currentCoroutineContext().ensureActive()
                DownloadController.progress(index, spec.items.size)
                updateNotification(index, spec.items.size)
                try {
                    val image = repository.downloadImage(
                        url = item.url,
                        referer = spec.article.url,
                        original = spec.preferOriginal,
                    )
                    val displayName = String.format(Locale.US, "%03d%s", item.number, image.extension)
                    val uri = try {
                        saver.save(image, spec.folderName, displayName)
                    } finally {
                        image.file.delete()
                    }
                    if (sampleUri == null) sampleUri = uri
                    ok++
                    AppLog.i("下载", "第 ${item.number} 张已保存：$displayName")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    AppLog.e("下载", "第 ${item.number} 张失败", e)
                }
                DownloadController.progress(index + 1, spec.items.size)
            }
            DownloadController.finish(ok, failed, displayFolder, false, sampleUri)
            AppLog.i("下载", "完成：成功 $ok 张，失败 $failed 张；目录 $displayFolder")
        } catch (e: CancellationException) {
            AppLog.w("下载", "已取消，已保存 $ok 张")
            DownloadController.finish(ok, failed, displayFolder, true, sampleUri)
        } catch (e: Exception) {
            AppLog.e("下载", "任务中止", e)
            DownloadController.fail(e.message ?: "下载失败")
        } finally {
            stopForegroundAndSelf()
        }
    }

    private fun displayFolder(folderName: String): String {
        val settings = Settings(this)
        return if (settings.useCustomDir) {
            "自定义目录/$folderName"
        } else {
            "Pictures/公众号图片/$folderName"
        }
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(done: Int, total: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("微信公众号图片下载器")
            .setContentText("正在下载 $done / $total")
            .setProgress(total.coerceAtLeast(1), done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, "取消", cancelIntent)
            .build()
    }

    private fun updateNotification(done: Int, total: Int) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(done, total))
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "图片下载",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "公众号图片下载进度"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.articleimages.downloader.action.START"
        const val ACTION_CANCEL = "com.articleimages.downloader.action.CANCEL"
        private const val CHANNEL_ID = "download"
        private const val NOTIFICATION_ID = 1001
    }
}