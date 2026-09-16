package org.communitypoke.fdroidai.userscript

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Userscript runtime for the in-app browser. Holds a snapshot of installed
 * scripts ([setScripts]), decides which run on a URL ([scriptsFor]), and
 * injects them into a dedicated [WebView] at `document-start`/`document-end`.
 *
 * Security model:
 * - Scripts only ever run on `http(s)` pages whose URL matches `@match` /
 *   `@include` (and is not `@exclude`d); other schemes are refused both at
 *   match time and in [shouldOverrideUrlLoading].
 * - The WebView this is attached to has file/content access disabled and the
 *   `GM_*` bridge is the only Java bridge exposed; each script only sees the
 *   functions it declared via `@grant`.
 * - `GM_*` values are scoped per script id in [GmValueStore].
 *
 * Note: the bridge object is visible to page JavaScript as `window.__fdroid_gm`
 * — unavoidable with `addJavascriptInterface` — so treat GM storage as
 * readable/writable by visited pages and never store secrets in it.
 */
class UserscriptRuntime(
    private val gmValues: GmValueStore,
    private val json: Json = Json,
    private val notifier: (title: String, text: String) -> Unit = { _, _ -> },
    private val logger: (String) -> Unit = {},
) {

    private val scripts = CopyOnWriteArrayList<Userscript>()

    /** Replace the active script set (called whenever the store changes). */
    fun setScripts(newScripts: List<Userscript>) {
        scripts.clear()
        scripts += newScripts
    }

    fun scriptsFor(url: String, runAt: RunAt): List<Userscript> =
        scripts.filter { it.enabled && it.metadata.runAt == runAt && it.matchesUrl(url) }

    /** Configure [webView] for userscript execution. Call once per WebView. */
    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    fun attach(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true // required for userscripts
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mediaPlaybackRequiresUserGesture = true
            safeBrowsingEnabled = true
        }
        webView.addJavascriptInterface(bridge, BRIDGE_NAME)
        webView.webViewClient = UserscriptClient()
    }

    fun detach(webView: WebView) {
        webView.removeJavascriptInterface(BRIDGE_NAME)
    }

    /** JavaScript payload that wraps [script]'s source with its GM bridge. */
    internal fun injectionFor(script: Userscript): String {
        val id = script.id.jsString()
        val grants = script.metadata.grants
        val sb = StringBuilder()
        sb.append("(function() {\n'use strict';\n")
        // Run-once guard per script per page.
        sb.append("window.__fdroid_us_once = window.__fdroid_us_once || {};\n")
        sb.append("if (window.__fdroid_us_once[").append(id).append("]) return;\n")
        sb.append("window.__fdroid_us_once[").append(id).append("] = true;\n")
        sb.append("const __bridge = window.").append(BRIDGE_NAME).append(";\n")
        sb.append("const __sid = ").append(id).append(";\n")

        if ("GM_info" in grants) {
            sb.append("const GM_info = ").append(gmInfo(script)).append(";\n")
        }
        if ("GM_getValue" in grants) {
            sb.append("function GM_getValue(k, d) { const v = __bridge.gmGetValue(__sid, String(k)); return v === '' ? d : JSON.parse(v); }\n")
        }
        if ("GM_setValue" in grants) {
            sb.append("function GM_setValue(k, v) { __bridge.gmSetValue(__sid, String(k), JSON.stringify(v === undefined ? null : v)); }\n")
        }
        if ("GM_deleteValue" in grants) {
            sb.append("function GM_deleteValue(k) { __bridge.gmDeleteValue(__sid, String(k)); }\n")
        }
        if ("GM_listValues" in grants) {
            sb.append("function GM_listValues() { return JSON.parse(__bridge.gmListValues(__sid)); }\n")
        }
        if ("GM_notification" in grants) {
            sb.append("function GM_notification(text, title) { __bridge.gmNotify(__sid, String(title || ''), String(text)); }\n")
        }
        if ("GM_log" in grants) {
            sb.append("function GM_log(msg) { __bridge.gmLog(__sid, String(msg)); }\n")
        }

        sb.append("try {\n")
        sb.append(script.source).append('\n')
        sb.append("} catch (__e) { __bridge.gmLog(__sid, 'userscript error: ' + __e); }\n")
        sb.append("})();")
        return sb.toString()
    }

    private fun gmInfo(script: Userscript): String = buildString {
        append("{scriptHandler:'F-Droid AI',version:'1.0',script:{")
        append("name:").append(script.metadata.name.jsString()).append(',')
        append("namespace:").append(script.metadata.namespace.jsString()).append(',')
        append("version:").append(script.metadata.version.jsString())
        append("}}")
    }

    /** JSON-encode a Kotlin string as a JS string literal. */
    private fun String.jsString(): String = json.encodeToString(this)

    private fun injectAll(webView: WebView, url: String?, runAt: RunAt) {
        if (url.isNullOrBlank()) return
        for (script in scriptsFor(url, runAt)) {
            webView.evaluateJavascript(injectionFor(script), null)
        }
    }

    inner class UserscriptClient : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            injectAll(view, url, RunAt.DOCUMENT_START)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            injectAll(view, url, RunAt.DOCUMENT_END)
            injectAll(view, url, RunAt.DOCUMENT_IDLE)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val scheme = request.url?.scheme?.lowercase()
            // Only http/https navigations are allowed — blocks intent:, file:,
            // data: and other scheme escapes.
            return scheme != "http" && scheme != "https"
        }
    }

    /** Java bridge for `GM_*` functions; every call is scoped to a script id. */
    private val bridge = object {
        @JavascriptInterface
        fun gmGetValue(scriptId: String, key: String): String =
            gmValues.getValue(scriptId, key)?.toString() ?: ""

        @JavascriptInterface
        fun gmSetValue(scriptId: String, key: String, valueJson: String) {
            try {
                gmValues.setValue(scriptId, key, json.parseToJsonElement(valueJson))
            } catch (e: Exception) {
                logger("GM_setValue parse error for $scriptId/$key")
            }
        }

        @JavascriptInterface
        fun gmDeleteValue(scriptId: String, key: String) =
            gmValues.deleteValue(scriptId, key)

        @JavascriptInterface
        fun gmListValues(scriptId: String): String =
            gmValues.listValues(scriptId).joinToString(",", "[", "]") {
                it.jsString()
            }

        @JavascriptInterface
        fun gmNotify(scriptId: String, title: String, text: String) =
            notifier(title.ifBlank { scriptId }, text)

        @JavascriptInterface
        fun gmLog(scriptId: String, message: String) = logger("[$scriptId] $message")
    }

    private companion object {
        const val BRIDGE_NAME = "__fdroid_gm"
    }
}
