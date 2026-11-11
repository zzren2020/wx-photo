package com.articleimages.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.articleimages.downloader.data.AppLog
import com.articleimages.downloader.ui.AppRoot
import com.articleimages.downloader.vm.HomeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {

    private val incomingText = MutableStateFlow<String?>(null)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        AppLog.i("权限", "通知权限：$granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
        requestNotificationPermissionIfNeeded()

        setContent {
            val viewModel: HomeViewModel = viewModel()
            val sharedText by incomingText.collectAsStateWithLifecycle()
            LaunchedEffect(sharedText) {
                val text = sharedText
                if (text != null) {
                    viewModel.onSharedText(text)
                    incomingText.value = null
                }
            }
            AppRoot(viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        if (!text.isNullOrBlank()) {
            incomingText.value = text
            AppLog.i("入口", "收到外部文本，长度 ${text.length}")
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
