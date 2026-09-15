package org.communitypoke.fdroidai.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.communitypoke.fdroidai.data.model.FdroidApp
import org.communitypoke.fdroidai.di.AppContainer
import org.communitypoke.fdroidai.ui.common.AppIcon
import org.communitypoke.fdroidai.ui.common.ErrorView
import org.communitypoke.fdroidai.ui.common.LoadingView
import org.communitypoke.fdroidai.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    container: AppContainer,
    packageName: String,
    onBack: () -> Unit,
) {
    val viewModel: AppDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AppDetailViewModel(container.fdroidRepository, packageName) }
        },
    )
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("App details") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
        when (val s = state) {
            is UiState.Loading -> LoadingView("Loading app…")
            is UiState.Error -> ErrorView(
                message = s.message,
                onRetry = if (s.canRetry) ({ viewModel.load() }) else null,
            )
            is UiState.Success -> AppDetailContent(app = s.data)
        }
    }
}

@Composable
private fun AppDetailContent(app: FdroidApp) {
    val context = LocalContext.current
    val openUrl: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(iconUrl = app.iconUrl, name = app.name)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(app.name, style = MaterialTheme.typography.headlineSmall)
                app.authorName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (app.categories.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(app.categories) { category ->
                    AssistChip(onClick = {}, label = { Text(category) })
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { openUrl(app.webUrl) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("View on F-Droid")
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            app.webSite?.let {
                OutlinedButton(onClick = { openUrl(it) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Language, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Website")
                }
            }
            app.sourceCode?.let {
                OutlinedButton(onClick = { openUrl(it) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Code, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Source")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        MetadataGrid(app)

        if (app.description.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = app.description.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetadataGrid(app: FdroidApp) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    val items = buildList {
        app.versionName?.let { add("Version" to it) }
        app.license?.let { add("License" to it) }
        app.apkSize?.let { add("APK size" to formatBytes(it)) }
        app.minSdkVersion?.let { add("Min SDK" to "API $it") }
        if (app.added > 0) add("Added" to dateFormat.format(Date(app.added)))
        if (app.lastUpdated > 0) add("Updated" to dateFormat.format(Date(app.lastUpdated)))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(110.dp),
                )
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
