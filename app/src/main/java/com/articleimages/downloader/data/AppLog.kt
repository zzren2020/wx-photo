package com.articleimages.downloader.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class LogEntry(
    val id: Long,
    val time: String,
    val level: Char,
    val tag: String,
    val message: String,
) {
    fun format(): String = "$time ${level}/$tag: $message"
}

/**
 * 应用内日志环形缓冲：
 *  - 日志页可单条勾选 / 全选后复制；
 *  - 同时镜像到 Logcat，方便 adb 调试。
 */
object AppLog {

    private const val MAX_ENTRIES = 2000

    private val nextId = AtomicLong(1L)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    fun d(tag: String, message: String) = append('D', tag, message)

    fun i(tag: String, message: String) = append('I', tag, message)

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        append('W', tag, message + (throwable?.let { ": ${it.message}" } ?: ""))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val suffix = throwable?.let { ": ${it.javaClass.simpleName}: ${it.message}" } ?: ""
        append('E', tag, message + suffix)
        if (throwable != null) Log.e(tag, message, throwable)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private fun append(level: Char, tag: String, message: String) {
        val entry = LogEntry(
            id = nextId.getAndIncrement(),
            time = synchronized(lock) { timeFormat.format(Date()) },
            level = level,
            tag = tag,
            message = message,
        )
        synchronized(lock) {
            _entries.update { old ->
                val kept = if (old.size >= MAX_ENTRIES) old.drop(old.size / 4) else old
                kept + entry
            }
        }
        val priority = when (level) {
            'E' -> Log.ERROR
            'W' -> Log.WARN
            'I' -> Log.INFO
            else -> Log.DEBUG
        }
        Log.println(priority, tag, message)
    }
}
