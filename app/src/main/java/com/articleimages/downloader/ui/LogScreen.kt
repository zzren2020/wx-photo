package com.articleimages.downloader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.articleimages.downloader.data.AppLog
import com.articleimages.downloader.data.LogEntry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val entries by AppLog.entries.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }

    fun copySelected() {
        val text = entries.filter { it.id in selected }.joinToString("\n") { it.format() }
        if (text.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("请先勾选要复制的日志") }
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("运行日志", text))
        scope.launch { snackbarHostState.showSnackbar("已复制 ${selected.size} 条日志") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("运行日志（${entries.size}）") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { selected = entries.map { it.id }.toSet() }) {
                    Text("全选")
                }
                TextButton(onClick = { selected = emptySet() }) {
                    Text("取消全选")
                }
                TextButton(onClick = { copySelected() }) {
                    Text("复制选中")
                }
                TextButton(onClick = {
                    AppLog.clear()
                    selected = emptySet()
                }) {
                    Text("清空")
                }
            }
            Text(
                text = "已选 ${selected.size} / 共 ${entries.size} 条；勾选后可复制，便于排查问题。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            HorizontalDivider()

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        LogRow(
                            entry = entry,
                            checked = entry.id in selected,
                            onToggle = {
                                selected = if (entry.id in selected) {
                                    selected - entry.id
                                } else {
                                    selected + entry.id
                                }
                            },
                            onLongPressCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("日志", entry.format()))
                                scope.launch { snackbarHostState.showSnackbar("已复制该条日志") }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogRow(
    entry: LogEntry,
    checked: Boolean,
    onToggle: () -> Unit,
    onLongPressCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle, onLongClick = onLongPressCopy)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(
            text = entry.format(),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = when (entry.level) {
                'E' -> MaterialTheme.colorScheme.error
                'W' -> Color(0xFFB26A00)
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}