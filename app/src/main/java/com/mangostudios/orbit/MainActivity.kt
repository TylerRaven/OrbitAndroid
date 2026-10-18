package com.mangostudios.orbit

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme

class MainActivity : ComponentActivity() {
    private lateinit var browser: Browser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        Browser.deleteStalePrivateProfiles()
        browser = Browser(this)
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
            Intent.ACTION_VIEW -> intent.dataString?.let { browser.newTab(url = it) }
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
