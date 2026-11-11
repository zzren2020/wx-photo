package com.articleimages.downloader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.articleimages.downloader.vm.HomeViewModel

@Composable
fun AppRoot(viewModel: HomeViewModel) {
    var screen by rememberSaveable { mutableStateOf("home") }
    var previewIndex by remember { mutableStateOf<Int?>(null) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2E7D32),
            secondary = Color(0xFF558B2F),
        )
    ) {
        val fallback = viewModel.fallbackUrl
        if (fallback != null) {
            WebExtractScreen(
                url = fallback,
                onExtracted = { title, images -> viewModel.applyExtracted(title, images) },
                onClose = { viewModel.fallbackUrl = null },
            )
        } else {
            when (screen) {
                "log" -> {
                    BackHandler { screen = "home" }
                    LogScreen(onBack = { screen = "home" })
                }

                "settings" -> {
                    BackHandler { screen = "home" }
                    SettingsScreen(onBack = { screen = "home" })
                }

                else -> HomeScreen(
                    viewModel = viewModel,
                    onOpenLog = { screen = "log" },
                    onOpenSettings = { screen = "settings" },
                    onPreview = { previewIndex = it },
                    onOpenFallback = { viewModel.startFallback() },
                )
            }

            val index = previewIndex
            val images = viewModel.entries
            if (index != null && images.isNotEmpty()) {
                PreviewDialog(
                    entries = images,
                    startIndex = index,
                    onClose = { previewIndex = null },
                )
            }
        }
    }
}
