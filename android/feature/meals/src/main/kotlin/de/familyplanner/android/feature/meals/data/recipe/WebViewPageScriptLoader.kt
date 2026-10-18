package de.familyplanner.android.feature.meals.data.recipe

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val PAGE_TIMEOUT_MS = 20_000L
private const val LD_JSON_SCRIPTS =
    "(function(){return Array.from(document.querySelectorAll('script[type=\"application/ld+json\"]'))" +
        ".map(function(s){return s.textContent;});})()"

/** Unsichtbare WebView (ADR-0012): ein echter Browser lädt die Seite, wir lesen nur die schema.org-Daten. */
class WebViewPageScriptLoader(
    private val context: Context,
) : PageScriptLoader {
    override suspend fun ldJsonScripts(url: String): List<String>? =
        withContext(Dispatchers.Main) {
            val webView = createWebView()
            try {
                withTimeoutOrNull(PAGE_TIMEOUT_MS) { awaitScripts(webView, url) }
            } finally {
                webView.stopLoading()
                webView.destroy()
            }
        }

    @SuppressLint("SetJavaScriptEnabled") // nötig für evaluateJavascript; keine JS-Bridge, kein Dateizugriff
    private fun createWebView() =
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkImage = true
        }

    private suspend fun awaitScripts(
        webView: WebView,
        url: String,
    ): List<String>? =
        suspendCancellableCoroutine { cont ->
            webView.webViewClient =
                object : WebViewClient() {
                    override fun onPageFinished(
                        view: WebView,
                        url: String?,
                    ) {
                        if (!cont.isActive) return
                        view.evaluateJavascript(LD_JSON_SCRIPTS) { raw -> if (cont.isActive) cont.resume(decodeScriptList(raw)) }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.isForMainFrame && cont.isActive) cont.resume(null)
                    }
                }
            webView.loadUrl(url)
        }
}
