package com.articleimages.downloader.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.articleimages.downloader.data.AppLog
import com.articleimages.downloader.data.DownloadController
import com.articleimages.downloader.vm.HomeViewModel
import com.articleimages.downloader.vm.UiImage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenLog: () -> Unit,
    onOpenSettings: () -> Unit,
    onPreview: (Int) -> Unit,
    onOpenFallback: () -> Unit,
) {
    val context = LocalContext.current
    val download by viewModel.downloadState.collectAsStateWithLifecycle()
    val selectedCount = viewModel.entries.count { it.selected }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.toast) {
        val message = viewModel.toast
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.toast = null
        }
    }

    val pasteFromClipboard: () -> Unit = {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = manager?.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(context)?.toString()
        } else {
            null
        }
        if (text.isNullOrBlank()) {
            viewModel.toast = "剪贴板为空"
        } else {
            viewModel.urlText = text
            AppLog.i("入口", "从剪贴板粘贴")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("微信公众号图片下载器") },
                actions = {
                    IconButton(onClick = onOpenLog) {
                        Icon(Icons.Default.List, contentDescription = "日志")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { DownloadBottomBar(viewModel, download) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .imePadding(),
        ) {
            OutlinedTextField(
                value = viewModel.urlText,
                onValueChange = { viewModel.urlText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("文章链接") },
                placeholder = { Text("https://mp.weixin.qq.com/s/...") },
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = pasteFromClipboard) { Text("粘贴") }
                },
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { viewModel.read(coverOnly = false) },
                    enabled = !viewModel.reading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("读取图片")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { viewModel.read(coverOnly = true) },
                    enabled = !viewModel.reading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("仅获取封面")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("优先原图", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = viewModel.preferOriginal,
                    onCheckedChange = { viewModel.setPreferOriginal(it) },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "已选 $selectedCount / ${viewModel.entries.size}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.selectAll() }, enabled = viewModel.entries.isNotEmpty()) {
                    Text("全选")
                }
                TextButton(onClick = { viewModel.invertSelection() }, enabled = viewModel.entries.isNotEmpty()) {
                    Text("反选")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenFallback) { Text("浏览器提取") }
            }

            if (viewModel.reading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("正在读取文章…", style = MaterialTheme.typography.bodySmall)
            }

            val error = viewModel.errorMessage
            if (error != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            val article = viewModel.article
            if (article != null) {
                Text(
                    text = "${article.title}  ·  共 ${article.images.size} 张",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            if (viewModel.entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "读取文章后，这里会显示可勾选的图片缩略图。\n点击缩略图切换勾选，长按查看大图。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(viewModel.entries, key = { _, item -> item.number }) { index, item ->
                        ImageCell(
                            item = item,
                            onClick = { viewModel.toggle(item.number) },
                            onPreview = { onPreview(index) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageCell(
    item: UiImage,
    onClick: () -> Unit,
    onPreview: () -> Unit,
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onPreview),
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.url,
                contentDescription = "第 ${item.number} 张",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            ) {
                Text(
                    text = "${item.number}",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Checkbox(
                checked = item.selected,
                onCheckedChange = { onClick() },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun DownloadBottomBar(
    viewModel: HomeViewModel,
    download: DownloadController.State,
) {
    val context = LocalContext.current
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
        ) {
            when (download) {
                is DownloadController.State.Running -> {
                    val total = download.total.coerceAtLeast(1)
                    Text(
                        "下载中 ${download.done} / ${download.total}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(
                        progress = { download.done.toFloat() / total },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    )
                    OutlinedButton(
                        onClick = { DownloadController.cancel() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("取消下载")
                    }
                }

                is DownloadController.State.Finished -> {
                    Text(
                        text = if (download.cancelled) {
                            "已取消：成功 ${download.ok} 张，失败 ${download.failed} 张"
                        } else {
                            "完成：成功 ${download.ok} 张，失败 ${download.failed} 张"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "保存位置：${download.folder}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sample = download.sampleUri
                    Row {
                        if (sample != null) {
                            TextButton(onClick = { openImage(context, sample) }) {
                                Text("查看已保存图片")
                            }
                        }
                        TextButton(onClick = { DownloadController.reset() }) { Text("关闭") }
                    }
                }

                is DownloadController.State.Failed -> {
                    Text(
                        "下载失败：${download.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { DownloadController.reset() }) { Text("关闭") }
                }

                DownloadController.State.Idle -> {
                    val idleSelected = viewModel.entries.count { it.selected }
                    Button(
                        onClick = { viewModel.downloadSelected() },
                        enabled = idleSelected > 0 && !viewModel.reading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("下载选中（$idleSelected 张）")
                    }
                }
            }
        }
    }
}

private fun openImage(context: Context, uri: String?) {
    if (uri == null) return
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uri), "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "查看图片"))
    } catch (e: Exception) {
        AppLog.w("界面", "无法打开图片：${e.message}")
    }
}