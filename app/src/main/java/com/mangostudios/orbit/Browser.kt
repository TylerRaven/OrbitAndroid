package com.mangostudios.orbit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.net.URLEncoder
import java.util.UUID

/** One browser tab. Its WebView is created lazily, so start-page tabs cost nothing. */
class Tab(val isPrivate: Boolean, private val browser: Browser) {
    val id: String = UUID.randomUUID().toString()

    var url by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var isLoading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var showingStart by mutableStateOf(true)

    /** Set when the current page's certificate was rejected; cleared by the next navigation. */
    var certBlocked by mutableStateOf<String?>(null)

    /** From the page's <meta name="theme-color">, when it has a real color. */
    var themeHue by mutableStateOf<Double?>(null)

    /** Bumped each time a page finishes opening; the page glow watches it. */
    var glowPulse by mutableIntStateOf(0)
    var thumbnail by mutableStateOf<ImageBitmap?>(null)

    private var lastUpgrade: String? = null
    private var lastPulseUrl: String? = null
    private var lastPulseAt = 0L

    var webView: WebView? = null
        private set

    val host: String? get() = url?.let { Uri.parse(it).host }?.removePrefix("www.")

    /** The page's color: its theme-color, a known site color, or one derived from the domain. */
    val glowHue: Double
        get() = when {
            isPrivate -> Hue.PRIVATE
            themeHue != null -> themeHue!!
            !showingStart && host != null -> Hue.site(host!!)
            else -> Hue.ACCENT
        }

    val displayTitle: String
        get() = when {
            showingStart -> if (isPrivate) "Private tab" else "New tab"
            title.isNotBlank() -> title
            else -> host ?: url ?: "Untitled"
        }

    /** Set for restored tabs, which load only once you look at them. */
    var pendingLoad = false

    fun ensureWebView(context: Context): WebView = webView ?: makeWebView(context).also { webView = it }

    /** The WebView to put on screen, loading a restored page on first show. */
    fun attach(context: Context): WebView {
        val wv = ensureWebView(context)
        if (pendingLoad) {
            pendingLoad = false
            url?.let { wv.loadUrl(it) }
        }
        return wv
    }

    fun load(input: String, context: Context) {
        val target = Browser.resolve(input)
        showingStart = false
        url = target
        ensureWebView(context).loadUrl(target)
    }

    fun goBack() {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else showStart()
    }

    fun goForward() {
        val wv = webView ?: return
        if (showingStart && url != null) showingStart = false else if (wv.canGoForward()) wv.goForward()
    }

    fun reload() = webView?.reload()
    fun stop() = webView?.stopLoading()

    /** Going back past the first page returns to the start page, like a fresh tab. */
    private fun showStart() {
        if (showingStart) return
        webView?.stopLoading()
        showingStart = true
        canGoForward = true
        isLoading = false
    }

    val canGoBackHere: Boolean get() = !showingStart && (canGoBack || url != null)
    val canGoForwardHere: Boolean get() = (showingStart && url != null) || (!showingStart && canGoForward)

    fun captureThumbnail() {
        val wv = webView
        if (showingStart || wv == null || wv.width == 0 || wv.height == 0) return
        val scale = 0.4f
        val bmp = createBitmap((wv.width * scale).toInt().coerceAtLeast(1), (wv.height * scale).toInt().coerceAtLeast(1))
        bmp.applyCanvas {
            scale(scale, scale)
            translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat())
            wv.draw(this)
        }
        thumbnail = bmp.asImageBitmap()
    }

    fun destroy() {
        webView?.apply {
            stopLoading()
            // Without a separate profile, at least leave no cache, form data or history behind.
            if (isPrivate && !Browser.privateProfileSupported) {
                clearCache(true)
                clearFormData()
                clearHistory()
            }
            webChromeClient = null
            (parent as? android.view.ViewGroup)?.removeView(this)
            destroy()
        }
        webView = null
    }

    private fun syncNav() {
        val wv = webView ?: return
        canGoBack = wv.canGoBack()
        canGoForward = wv.canGoForward()
    }

    private fun pulse(pageUrl: String?) {
        val now = System.currentTimeMillis()
        // WebView often reports "finished" twice for one load; only glow once.
        if (pageUrl == lastPulseUrl && now - lastPulseAt < 1500) return
        lastPulseUrl = pageUrl
        lastPulseAt = now
        glowPulse++
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun makeWebView(context: Context): WebView {
        val wv = WebView(context)
        if (isPrivate) Browser.usePrivateProfile(wv)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            if (isPrivate && !Browser.privateProfileSupported) cacheMode = WebSettings.LOAD_NO_CACHE

            // Hardening: pages get no file:// or content:// access, never mix http into https,
            // and Safe Browsing screens every navigation.
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            setGeolocationEnabled(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, !isPrivate)
        wv.setBackgroundColor(android.graphics.Color.WHITE)

        wv.setDownloadListener { dlUrl, userAgent, contentDisposition, mimeType, length ->
            browser.requestDownload(this, dlUrl, userAgent, contentDisposition, mimeType, length)
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme?.lowercase()
                if (scheme == "https") return false
                if (scheme == "http") {
                    if (!request.isForMainFrame || Browser.isLocal(uri.host)) return false
                    // HTTPS-first: cleartext is off app-wide, so upgrade instead of failing.
                    val upgraded = uri.buildUpon().scheme("https").build().toString()
                    if (upgraded == lastUpgrade) return false // https bounced us back to http; let it fail
                    lastUpgrade = upgraded
                    view.loadUrl(upgraded)
                    return true
                }
                if (scheme == "about" && uri.toString() == "about:blank") return false
                // Everything else (mailto:, tel:, intent://, market:…) needs a tap from the user, and
                // only ever goes out through a confirmed hand-off. file:, content:, javascript: are dropped.
                if (!request.hasGesture() && !request.isRedirect) return true
                when (val d = External.decide(view.context, uri)) {
                    External.Decision.Block -> {}
                    is External.Decision.Load -> view.loadUrl(d.url)
                    is External.Decision.Ask -> browser.prompt = d.prompt
                }
                return true
            }

            /** Certificate problems are never waved through. */
            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                isLoading = false
                certBlocked = Uri.parse(error.url).host ?: "this site"
            }

            override fun onPageStarted(view: WebView, pageUrl: String?, favicon: Bitmap?) {
                if (pageUrl != null && pageUrl != "about:blank") url = pageUrl
                isLoading = true
                certBlocked = null
                showingStart = false
                syncNav()
            }

            override fun doUpdateVisitedHistory(view: WebView, pageUrl: String?, isReload: Boolean) {
                if (pageUrl != null && pageUrl != "about:blank") url = pageUrl
                syncNav()
            }

            override fun onPageFinished(view: WebView, pageUrl: String?) {
                isLoading = false
                progress = 100
                syncNav()
                if (pageUrl != null && pageUrl != "about:blank") url = pageUrl
                view.title?.takeIf { it.isNotBlank() }?.let { title = it }
                // Read theme-color first so the glow comes up in the page's own color.
                view.evaluateJavascript(
                    "(document.querySelector('meta[name=\"theme-color\"]') || {}).content || ''"
                ) { raw ->
                    val css = raw?.trim('"').orEmpty()
                    themeHue = if (css.isNotEmpty()) Hue.fromCss(css) else null
                    pulse(pageUrl)
                    browser.saveSession()
                }
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                // isLoading follows onPageStarted/onPageFinished only, so a link handed to another
                // app (which never "finishes") doesn't leave the stop button stuck on.
                progress = newProgress
            }

            override fun onReceivedTitle(view: WebView, newTitle: String?) {
                if (!newTitle.isNullOrBlank()) title = newTitle
            }

            /** target=_blank and window.open() land in a new tab next to this one. */
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (!isUserGesture) return false
                val tab = browser.newTab(isPrivate = isPrivate, after = this@Tab)
                tab.showingStart = false
                val child = tab.ensureWebView(view.context)
                (resultMsg.obj as WebView.WebViewTransport).webView = child
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                browser.close(this@Tab)
            }

            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                val chooser = browser.fileChooser ?: return false
                return chooser(callback, params)
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (browser.fullscreen != null) {
                    callback.onCustomViewHidden()
                    return
                }
                browser.fullscreen = FullscreenView(view, callback, this@Tab)
            }

            override fun onHideCustomView() {
                browser.exitFullscreen(fromPage = true)
            }
        }
        return wv
    }
}

/** All open tabs and which one is showing. */
class Browser(val context: Context) {
    val tabs = mutableStateListOf<Tab>()
    var selected by mutableStateOf<Tab?>(null)
    var showingSwitcher by mutableStateOf(false)

    /** A confirmation the page is waiting on (external app, download). */
    var prompt by mutableStateOf<Prompt?>(null)
    var fullscreen by mutableStateOf<FullscreenView?>(null)

    /** Set by the activity, which owns the system file picker. */
    var fileChooser: ((ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean)? = null

    /** Set by the activity, which asks for storage permission on Android 9 and older. */
    var startDownload: ((Download) -> Unit) = { Downloads.enqueue(context, it) }

    fun exitFullscreen(fromPage: Boolean = false) {
        val f = fullscreen ?: return
        fullscreen = null
        if (!fromPage) f.callback.onCustomViewHidden()
    }

    fun requestDownload(tab: Tab, url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, length: Long) {
        val scheme = Uri.parse(url).scheme?.lowercase()
        if (scheme != "https" && scheme != "http") {
            Toast.makeText(context, "Orbit can't save this kind of download yet", Toast.LENGTH_SHORT).show()
            return
        }
        val cookies = if (tab.isPrivate) privateCookieManager()?.getCookie(url) else CookieManager.getInstance().getCookie(url)
        prompt = Prompt.ConfirmDownload(
            Downloads.describe(url, userAgent, contentDisposition, mimeType, length, cookies, tab.url, tab.isPrivate)
        )
    }

    fun newTab(isPrivate: Boolean = false, url: String? = null, after: Tab? = null): Tab {
        val tab = Tab(isPrivate, this)
        val index = after?.let { tabs.indexOf(it) + 1 }?.takeIf { it > 0 } ?: tabs.size
        tabs.add(index, tab)
        selected = tab
        showingSwitcher = false
        if (url != null) tab.load(url, context)
        return tab
    }

    fun select(tab: Tab) {
        selected = tab
        showingSwitcher = false
    }

    fun close(tab: Tab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        if (fullscreen?.tab == tab) exitFullscreen()
        tabs.removeAt(index)
        tab.destroy()
        if (selected == tab) selected = tabs.getOrNull(index) ?: tabs.getOrNull(index - 1)
        if (tab.isPrivate && tabs.none { it.isPrivate }) clearPrivateData()
        if (tabs.isEmpty()) {
            newTab()
            showingSwitcher = true
        }
        saveSession()
    }

    fun openSwitcher() {
        selected?.captureThumbnail()
        showingSwitcher = true
    }

    // MARK: Session

    private val prefs get() = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    /** Private tabs are never written down. */
    fun saveSession() {
        val normal = tabs.filter { !it.isPrivate }
        val urls = normal.map { if (it.showingStart) "" else it.url.orEmpty() }
        prefs.edit()
            .putString("urls", urls.joinToString("\n"))
            .putInt("selected", normal.indexOf(selected).coerceAtLeast(0))
            .apply()
    }

    fun restoreSession() {
        val urls = prefs.getString("urls", null)?.split("\n").orEmpty()
        urls.forEach { u ->
            val tab = newTab()
            if (u.isNotEmpty()) {
                tab.url = u
                tab.showingStart = false
                tab.pendingLoad = true
            }
        }
        if (tabs.isEmpty()) newTab() else selected = tabs.getOrNull(prefs.getInt("selected", 0)) ?: tabs.last()
    }

    /** The private session ends with the last private tab: its profile goes, and the next one starts fresh. */
    private fun clearPrivateData() {
        if (!privateProfileSupported) return
        val ended = privateProfile
        privateSession++
        // The WebViews were just destroyed; give WebView a moment to let go of the profile.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching { ProfileStore.getInstance().deleteProfile(ended) }
        }, 500)
    }

    companion object {
        private const val PRIVATE_PREFIX = "orbit-private-"
        private var privateSession = System.currentTimeMillis()
        private val privateProfile get() = PRIVATE_PREFIX + privateSession

        val privateProfileSupported: Boolean by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }

        fun privateCookieManager(): CookieManager? =
            if (privateProfileSupported) ProfileStore.getInstance().getProfile(privateProfile)?.cookieManager else CookieManager.getInstance()

        /** Private tabs share their own cookie jar and storage, separate from normal tabs. */
        fun usePrivateProfile(webView: WebView) {
            if (!privateProfileSupported) return
            ProfileStore.getInstance().getOrCreateProfile(privateProfile)
            WebViewCompat.setProfile(webView, privateProfile)
        }

        /** Sweeps private profiles a previous run didn't get to delete (e.g. the app was killed). */
        fun deleteStalePrivateProfiles() {
            if (!privateProfileSupported) return
            val store = ProfileStore.getInstance()
            store.allProfileNames.filter { it.startsWith(PRIVATE_PREFIX) }.forEach { name ->
                runCatching { store.deleteProfile(name) }
            }
        }

        private val hostLike = Regex("^(localhost|[^\\s/]+\\.[a-zA-Z]{2,}|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?(/.*)?$")

        /** Hosts that may still use plain http (see res/xml/network_security_config.xml). */
        fun isLocal(host: String?) = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"

        /**
         * A URL as typed, a bare domain (https:// added), or a Google search. Only http(s) and
         * about:blank are ever loaded as-is — javascript:, file:, data: and the rest become searches.
         * http:// is upgraded to https:// except for local development hosts.
         */
        fun resolve(input: String): String {
            val text = input.trim()
            if (!text.contains(' ')) {
                val uri = Uri.parse(text)
                when (uri.scheme?.lowercase()) {
                    "https" -> if (uri.host != null) return text
                    "http" -> if (uri.host != null) return if (isLocal(uri.host)) text else "https://" + text.substringAfter("://")
                }
                if (text == "about:blank") return text
                if (hostLike.matches(text)) return if (isLocal(Uri.parse("http://$text").host)) "http://$text" else "https://$text"
            }
            return "https://www.google.com/search?q=" + URLEncoder.encode(text, "UTF-8")
        }
    }
}
