package app.paper2test.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
fun WebScreen(nav: Nav, path: String, title: String, exam: Boolean = false) {
    val ctx = LocalContext.current
    val app = App.of(ctx)
    var web by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    val site = BuildConfig.SITE
    val url = remember(path) {
        val tok = app.session.token
        val (base, hash) = if (path.contains('#')) path.substringBefore('#') to "#" + path.substringAfter('#') else path to ""
        site + base + (if (tok != null) (if (base.contains('?')) "&" else "?") + "tok=" + Uri.encode(tok) else "") + hash
    }
    // Hardware back navigates inside the page first (exam palette etc.), then leaves the screen.
    BackHandler { if (web?.canGoBack() == true && !exam) web?.goBack() else nav.back() }
    DisposableEffect(exam) {
        val a = ctx as? android.app.Activity
        if (exam) a?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { a?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Scaffold(topBar = {
        if (!exam) TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AndroidView(factory = { c ->
                WebView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.userAgentString = settings.userAgentString + " Paper2TestApp/1.0"
                    // Without a WebChromeClient, confirm()/alert() silently return false — the exam's
                    // "Submit now?" prompt would never be answered and the button would look dead.
                    webChromeClient = object : WebChromeClient() {
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
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val u = request.url
                            // Stay inside for our site; everything else (WhatsApp share, Razorpay, docs) goes to the system.
                            if (u.host == Uri.parse(site).host) return false
                            runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, u)) }
                            return true
                        }
                        override fun onPageFinished(view: WebView, url: String) { loading = false }
                    }
                    loadUrl(url)
                    web = this
                }
            }, modifier = Modifier.fillMaxSize())
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
