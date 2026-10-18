package com.mangostudios.orbit

import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme

class MainActivity : ComponentActivity() {
    private lateinit var browser: Browser

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val pickFiles = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uris = when {
            result.resultCode != RESULT_OK || data == null -> null
            data.clipData != null -> Array(data.clipData!!.itemCount) { data.clipData!!.getItemAt(it).uri }
            data.data != null -> arrayOf(data.data!!)
            else -> null
        }
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    private var waitingDownload: Download? = null
    private val askStorage = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val d = waitingDownload ?: return@registerForActivityResult
        waitingDownload = null
        if (granted) Downloads.enqueue(this, d)
        else Toast.makeText(this, "Orbit needs storage access to save downloads", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        Browser.deleteStalePrivateProfiles()
        browser = Browser(this)
        browser.fileChooser = { callback, params ->
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            val intent = params.createIntent().apply {
                if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            try {
                pickFiles.launch(intent)
                true
            } catch (e: ActivityNotFoundException) {
                fileCallback = null
                false
            }
        }
        browser.startDownload = { d ->
            val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                waitingDownload = d
                askStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                Downloads.enqueue(this, d)
            }
        }
        browser.restoreSession()
        handle(intent)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Hue.accent, secondary = Hue.accentLight)) {
                BrowserScreen(browser)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Links opened from other apps, and web searches, each get a new tab. */
    private fun handle(intent: Intent?) {
        when (intent?.action) {
            // Only web links; the manifest filters say so, but an explicit intent could carry anything.
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == "http" || it.scheme == "https" }
                ?.let { browser.newTab(url = it.toString()) }
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(SearchManager.QUERY)?.let { browser.newTab(url = it) }
        }
    }

    override fun onStop() {
        super.onStop()
        browser.saveSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) browser.tabs.forEach { it.destroy() }
    }
}
