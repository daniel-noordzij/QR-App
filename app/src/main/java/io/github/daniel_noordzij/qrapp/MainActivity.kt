package io.github.daniel_noordzij.qrapp

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import io.github.daniel_noordzij.qrapp.ui.QrApp
import io.github.daniel_noordzij.qrapp.ui.QrAppTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        // Keep scanned text out of the task snapshot the system stores for the Recents screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        if (savedInstanceState == null) {
            handleSharedImage(intent)
        }
        setContent {
            QrAppTheme {
                QrApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedImage(intent)
    }

    /** Handles an image shared to the app from another app's share sheet. */
    private fun handleSharedImage(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("image/") != true) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            ?: return
        // Only accept images shared through a content provider, never raw file paths.
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return
        viewModel.scanImage(uri)
    }
}
