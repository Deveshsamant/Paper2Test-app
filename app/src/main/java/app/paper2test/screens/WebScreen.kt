package app.paper2test.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.paper2test.*

/**
 * The website inside the app. The exam room and the host/review/results/store screens are the same tested pages
 * the browser uses; the session is handed over once via ?tok=, and the site hides its own chrome for our user agent.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebScreen(nav: Nav, path: String, title: String, exam: Boolean = false, tab: String? = null) {
    val ctx = LocalContext.current
    val app = App.of(ctx)
    var web by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var canBack by remember { mutableStateOf(false) } // moved on inside the page (e.g. Store -> a bundle)
    // <input type="file"> in the page (e.g. importing students from Excel): Android's file picker.
    var fileCb by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        fileCb?.onReceiveValue(uri?.let { arrayOf(it) } ?: emptyArray()); fileCb = null
    }
    val site = BuildConfig.SITE
    val url = remember(path) {
        val tok = app.session.token
        val (base, hash) = if (path.contains('#')) path.substringBefore('#') to "#" + path.substringAfter('#') else path to ""
        // Session and the app's current space handed to the website once (it keeps them for its own requests).
        val params = listOfNotNull(tok?.let { "tok=" + Uri.encode(it) }, app.session.space?.let { "space=" + Uri.encode(it) })
        site + base + (if (params.isNotEmpty()) (if (base.contains('?')) "&" else "?") + params.joinToString("&") else "") + hash
    }
    // Hardware back navigates inside the page first (exam palette etc.), then leaves the screen.
    BackHandler { if (web?.canGoBack() == true && !exam) web?.goBack() else nav.back() }
    DisposableEffect(exam) {
        val a = ctx as? android.app.Activity
        if (exam) a?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            a?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            a?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // One back button: this arrow (the website hides its own "← Back" links in the app). It goes back inside the
    // page first, like the phone's Back. A tab's first page has no arrow: the bottom bar is the way around.
    Scaffold(topBar = {
        if (!exam) TopAppBar(title = { Text(title) }, navigationIcon = {
            if (tab == null || canBack) IconButton(onClick = { if (web?.canGoBack() == true) web?.goBack() else nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }, bottomBar = { if (tab != null && !exam) TabBar(nav, tab) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AndroidView(factory = { c ->
                WebView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.userAgentString = settings.userAgentString + " Paper2TestApp/1.0"
                    // The exam page calls P2TApp.secure(true) while a student is answering: no screenshots or screen
                    // recording then (results can still be shared). It only toggles this one window flag.
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun secure(on: Boolean) {
                            val w = (c as? android.app.Activity)?.window ?: return
                            post { if (on) w.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
                        }
                        /** A page that is finished (e.g. the welcome steps) returns to the app's previous screen. */
                        @JavascriptInterface
                        fun close() { post { nav.back() } }
                        /** Email sign-in / sign-up finished on the website's page: the app keeps the session and opens Home. */
                        @JavascriptInterface
                        fun signedIn(token: String) { if (token.length in 20..2000) post { app.session.token = token; nav.replace(Screen.Home) } }
                        /** The site's Pricing page inside the app: the native Plans screen (Google Play Billing). */
                        @JavascriptInterface
                        fun openPlans() { post { nav.go(Screen.Plans) } }
                        /** A file made by the page (Excel export, sample sheet): saved to Downloads. */
                        @JavascriptInterface
                        fun saveFile(name: String, base64: String, mime: String) {
                            val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120).ifBlank { "file" }
                            val ok = runCatching { saveToDownloads(c, safe, android.util.Base64.decode(base64, android.util.Base64.DEFAULT), mime) }.isSuccess
                            post { android.widget.Toast.makeText(c, if (ok) "Saved to Downloads: $safe" else "Could not save $safe", android.widget.Toast.LENGTH_LONG).show() }
                        }
                    }, "P2TApp")
                    // Without a WebChromeClient, confirm()/alert() silently return false — the exam's
                    // "Submit now?" prompt would never be answered and the button would look dead.
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                            fileCb?.onReceiveValue(null); fileCb = cb
                            // accept=".xlsx,.csv,<mime types>": give the picker the mime types (anything if none).
                            val types = params.acceptTypes.flatMap { it.split(',') }.map { it.trim() }.filter { it.contains('/') }
                                .plus(if (params.acceptTypes.any { it.contains(".csv") }) listOf("text/csv", "text/comma-separated-values", "application/vnd.ms-excel") else emptyList())
                            return runCatching { picker.launch((types.ifEmpty { listOf("*/*") }).distinct().toTypedArray()); true }.getOrElse { fileCb = null; false }
                        }
                        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                            android.app.AlertDialog.Builder(c).setMessage(message)
                                .setPositiveButton("OK") { _, _ -> result.confirm() }
                                .setNegativeButton("Cancel") { _, _ -> result.cancel() }
                                .setOnCancelListener { result.cancel() }.show()
                            return true
                        }
                        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                            android.app.AlertDialog.Builder(c).setMessage(message)
                                .setPositiveButton("OK") { _, _ -> result.confirm() }
                                .setOnCancelListener { result.confirm() }.show()
                            return true
                        }
                    }
                    // Study material the publisher allows to download: hand the (short-lived, signed) link to Android's
                    // download manager, which saves it to Downloads and shows progress.
                    setDownloadListener { dlUrl, _, disposition, mime, _ ->
                        runCatching {
                            val name = android.webkit.URLUtil.guessFileName(dlUrl, disposition, mime)
                            val req = android.app.DownloadManager.Request(Uri.parse(dlUrl)).setTitle(name).setMimeType(mime)
                                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            // Android 10+: straight into Downloads (older versions would need a storage permission).
                            if (android.os.Build.VERSION.SDK_INT >= 29) req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
                            (c.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(req)
                            android.widget.Toast.makeText(c, "Downloading $name", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val u = request.url
                            // Stay inside for our site; everything else (WhatsApp share, Razorpay, docs) goes to the system.
                            if (u.host == Uri.parse(site).host) return false
                            runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, u)) }
                            return true
                        }
                        override fun onPageFinished(view: WebView, url: String) { loading = false; canBack = view.canGoBack() }
                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) { canBack = view.canGoBack() }
                    }
                    loadUrl(url)
                    web = this
                }
            }, modifier = Modifier.fillMaxSize())
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

/** Saves bytes as a file in Downloads (Android 10+), or the app's own Downloads folder on older phones. */
private fun saveToDownloads(ctx: android.content.Context, name: String, bytes: ByteArray, mime: String) {
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
        }
        val uri = ctx.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
        ctx.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
    } else {
        val dir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
        java.io.File(dir, name).writeBytes(bytes)
    }
}
