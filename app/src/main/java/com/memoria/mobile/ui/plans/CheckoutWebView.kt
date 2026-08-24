package com.memoria.mobile.ui.plans

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.memoria.mobile.ui.common.BackTopBar

/**
 * The gateway checkout, rendered inside a MemorIA screen.
 *
 * A Custom Tab was the first attempt, but it is not guaranteed: on a device with
 * no Custom Tabs provider it falls back to an "Open with" chooser and throws the
 * user out to a browser — the exact thing this screen exists to avoid. A WebView
 * is deterministic: the checkout always stays inside the app.
 *
 * MemorIA does not touch the card. There is no JavaScript bridge here and
 * nothing reads the page: it just hosts Mercado Pago's own https page and
 * watches for the return URL that says the flow is over.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CheckoutWebView(
    url: String,
    /** Host of the configured back_url; reaching it means the flow is finished. */
    returnHost: String?,
    onFinished: () -> Unit,
    onCancel: () -> Unit,
) {
    var progress by remember { mutableStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // System back walks the checkout's own history first, so a mistyped card
    // does not dump the user out of the whole flow.
    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onCancel()
    }

    Scaffold(
        topBar = { BackTopBar("Pagamento seguro", onCancel) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webView = this
                        settings.apply {
                            // Mercado Pago's checkout does not render without these.
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            // Nothing typed on the gateway page is cached by the app:
                            // no autofill store, and the WebView keeps no cookies
                            // or cache of its own beyond this checkout.
                            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val target = request.url
                                val scheme = target.scheme?.lowercase()
                                // Anything that is not plain web navigation (a
                                // banking app link, mailto:, intent:) is refused
                                // rather than handed to another app.
                                if (scheme != "http" && scheme != "https") return true

                                if (returnHost != null && target.host?.contains(returnHost) == true) {
                                    onFinished()
                                    return true
                                }
                                return false
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                progress = 1
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                progress = 100
                            }
                        }
                        loadUrl(url)
                    }
                },
            )
        }
    }
}
