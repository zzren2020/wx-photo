package com.articleimages.downloader.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.articleimages.downloader.BuildConfig
import com.articleimages.downloader.data.AppLog
import com.articleimages.downloader.data.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }

    var preferOriginal by remember { mutableStateOf(settings.preferOriginal) }
    var useCustomDir by remember { mutableStateOf(settings.useCustomDir) }
    var treeUri by remember { mutableStateOf(settings.treeUri) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            settings.treeUri = uri.toString()
            settings.useCustomDir = true
            treeUri = uri.toString()
            useCustomDir = true
            AppLog.i("设置", "自定义目录：$uri")
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: Exception) {
                AppLog.w("设置", "目录权限可能无法持久化：${e.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SettingSwitchRow(
                title = "优先下载原图",
                subtitle = "打开后优先尝试 qpic 的 /0 原图地址，失败自动回退缩略图。",
                checked = preferOriginal,
                onCheckedChange = {
                    preferOriginal = it
                    settings.preferOriginal = it
                    AppLog.i("设置", "优先原图：$it")
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            SettingSwitchRow(
                title = "保存到自定义目录",
                subtitle = "关闭时保存到系统相册 Pictures/公众号图片；打开后使用下面选择的目录（可保存到 SD 卡）。",
                checked = useCustomDir,
                onCheckedChange = {
                    useCustomDir = it
                    settings.useCustomDir = it
                    AppLog.i("设置", "自定义目录开关：$it")
                },
            )

            TextButton(
                onClick = { picker.launch(null) },
                enabled = useCustomDir,
            ) {
                Text(if (treeUri.isNullOrBlank()) "选择自定义目录" else "重新选择目录")
            }

            Text(
                text = treeUri ?: "尚未选择自定义目录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            Text("默认保存位置", style = MaterialTheme.typography.titleSmall)
            Text(
                "相册 / Pictures / 公众号图片 / <时间戳_标题_随机4位>/001.jpg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Text("关于", style = MaterialTheme.typography.titleSmall)
            Text(
                "版本 ${BuildConfig.VERSION_NAME}\n" +
                    "功能：读取公众号文章正文图片与封面，勾选后下载到本机。\n" +
                    "网络：仅访问文章与图片地址，不做上传或统计。\n" +
                    "声明：内容版权归原作者/公众号所有，请仅用于个人存档，遵守微信平台规则。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}