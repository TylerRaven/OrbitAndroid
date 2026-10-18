package com.mangostudios.orbit

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.view.View
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.widget.Toast

/** Things a page asks for that need the user to say yes first. */
sealed interface Prompt {
    /** A link for another app (mailto:, tel:, intent://…). Never opened without a tap on "Open". */
    data class OpenExternal(val intent: Intent, val appLabel: String?, val description: String) : Prompt

    data class ConfirmDownload(val download: Download) : Prompt
}

/** A page's video (or other element) that has gone fullscreen. */
class FullscreenView(val view: View, val callback: WebChromeClient.CustomViewCallback, val tab: Tab)

data class Download(
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val userAgent: String?,
    val cookies: String?,
    val referer: String?,
    val length: Long,
    val fromPrivateTab: Boolean,
) {
    val sizeLabel: String?
        get() = when {
            length <= 0 -> null
            length < 1024 * 1024 -> "${(length + 1023) / 1024} KB"
            else -> "%.1f MB".format(length / (1024.0 * 1024.0))
        }
}

object Downloads {
    fun describe(
        url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, length: Long,
        cookies: String?, referer: String?, fromPrivateTab: Boolean,
    ) = Download(
        url = url,
        fileName = URLUtil.guessFileName(url, contentDisposition, mimeType).replace(Regex("[/\\\\:*?\"<>|]"), "_"),
        mimeType = mimeType,
        userAgent = userAgent,
        cookies = cookies,
        referer = referer,
        length = length,
        fromPrivateTab = fromPrivateTab,
    )

    /** Hands the file to the system DownloadManager: a progress notification, then a file in Downloads. */
    fun enqueue(context: Context, d: Download) {
        val request = DownloadManager.Request(Uri.parse(d.url)).apply {
            setTitle(d.fileName)
            setDescription(Uri.parse(d.url).host ?: "Orbit")
            d.mimeType?.let { setMimeType(it) }
            d.userAgent?.let { addRequestHeader("User-Agent", it) }
            d.cookies?.let { addRequestHeader("Cookie", it) }
            d.referer?.let { addRequestHeader("Referer", it) }
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, d.fileName)
        }
        runCatching {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(context, "Downloading ${d.fileName}", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Couldn't start the download", Toast.LENGTH_SHORT).show()
        }
    }
}

object External {
    /** Schemes that must never leave the browser or be followed from a page. */
    private val blocked = setOf("javascript", "file", "content", "data", "blob", "chrome", "about", "filesystem", "view-source")

    sealed interface Decision {
        data object Block : Decision
        data class Load(val url: String) : Decision
        data class Ask(val prompt: Prompt.OpenExternal) : Decision
    }

    /**
     * Works out what to do with a non-web link. intent:// URIs are stripped of explicit components,
     * selectors and permission-grant flags, so a page can only reach exported, BROWSABLE activities —
     * and only after the user confirms.
     */
    fun decide(context: Context, uri: Uri): Decision {
        val scheme = uri.scheme?.lowercase() ?: return Decision.Block
        if (scheme in blocked) return Decision.Block
        val intent = try {
            if (scheme == "intent") Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, uri)
        } catch (e: Exception) {
            return Decision.Block
        }
        val fallback = intent.getStringExtra("browser_fallback_url")
            ?.takeIf { val s = Uri.parse(it).scheme?.lowercase(); s == "https" || s == "http" }
        intent.apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            component = null
            selector = null
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        // An intent:// that just wraps a web URL stays in Orbit.
        val target = intent.data?.scheme?.lowercase()
        if (scheme == "intent" && intent.`package` == null && (target == "http" || target == "https")) {
            return Decision.Load(intent.dataString!!)
        }
        val pm = context.packageManager
        val match = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (match == null && fallback != null) return Decision.Load(fallback)
        val label = match?.activityInfo?.takeIf { it.packageName != "android" }?.loadLabel(pm)?.toString()
        val description = when (scheme) {
            "mailto" -> "Write an email to ${uri.schemeSpecificPart.substringBefore('?')}"
            "tel" -> "Call ${uri.schemeSpecificPart}"
            "sms", "smsto" -> "Text ${uri.schemeSpecificPart.substringBefore('?')}"
            else -> "This page wants to open another app."
        }
        return Decision.Ask(Prompt.OpenExternal(intent, label, description))
    }

    fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app on this phone can open that link", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, "That app can't be opened from a web page", Toast.LENGTH_SHORT).show()
        }
    }
}
