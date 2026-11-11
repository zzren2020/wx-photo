package com.articleimages.downloader.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.articleimages.downloader.core.Article
import com.articleimages.downloader.core.DownloadItem
import com.articleimages.downloader.service.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 下载任务的进程内单例：
 *  - UI 只负责提交任务与观察状态；
 *  - 实际下载在前台 Service 中执行，离开界面也能继续。
 */
object DownloadController {

    data class Job(
        val article: Article,
        val items: List<DownloadItem>,
        val preferOriginal: Boolean,
        val folderName: String,
    )

    sealed interface State {
        data object Idle : State
        data class Running(val done: Int, val total: Int) : State
        data class Finished(
            val ok: Int,
            val failed: Int,
            val folder: String,
            val cancelled: Boolean,
            val sampleUri: String?,
        ) : State

        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    @Volatile
    private var pending: Job? = null

    @Volatile
    private var runningJob: kotlinx.coroutines.Job? = null

    @Volatile
    var cancelRequested: Boolean = false
        private set

    /** @return true 表示任务已被接受并提交给前台服务。 */
    fun start(context: Context, job: Job): Boolean {
        if (_state.value is State.Running) {
            AppLog.w("下载", "已有下载任务在运行，忽略本次提交")
            return false
        }
        pending = job
        cancelRequested = false
        _state.value = State.Running(0, job.items.size)
        val intent = Intent(context, DownloadService::class.java)
            .setAction(DownloadService.ACTION_START)
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            AppLog.e("下载", "启动下载服务失败", e)
            pending = null
            _state.value = State.Failed(e.message ?: "无法启动下载服务")
            false
        }
    }

    fun cancel() {
        cancelRequested = true
        runningJob?.cancel()
    }

    internal fun takePending(): Job? = pending.also { pending = null }

    internal fun attach(job: kotlinx.coroutines.Job?) {
        runningJob = job
    }

    internal fun progress(done: Int, total: Int) {
        _state.value = State.Running(done, total)
    }

    internal fun finish(
        ok: Int,
        failed: Int,
        folder: String,
        cancelled: Boolean,
        sampleUri: String?,
    ) {
        _state.value = State.Finished(ok, failed, folder, cancelled, sampleUri)
        runningJob = null
    }

    internal fun fail(message: String) {
        _state.value = State.Failed(message)
        runningJob = null
    }

    fun reset() {
        if (_state.value !is State.Running) {
            _state.value = State.Idle
        }
    }
}
