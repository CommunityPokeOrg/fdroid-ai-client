package org.communitypoke.fdroidai.ui.scripts

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.communitypoke.fdroidai.di.AppContainer

/**
 * Minimal in-app browser wired to [org.communitypoke.fdroidai.userscript.UserscriptRuntime]:
 * enabled scripts are injected into every http(s) page that matches them.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ScriptBrowserScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("https://f-droid.org") }
    val webView = remember {
        WebView(context).also { container.userscriptRuntime.attach(it) }
    }

    // Keep the runtime's active script set fresh while the browser is open.
    LaunchedEffect(Unit) {
        container.userscriptRuntime.setScripts(container.userscriptStore.list())
        webView.loadUrl(normalizeUrl(url))
    }
    DisposableEffect(Unit) {
        onDispose {
            container.userscriptRuntime.detach(webView)
            webView.destroy()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("https://…") },
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { webView.loadUrl(normalizeUrl(url)) }),
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { webView.reload() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reload")
            }
        }
        Spacer(Modifier.height(8.dp))
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    return if ("://" in trimmed) trimmed else "https://$trimmed"
}
