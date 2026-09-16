package org.communitypoke.fdroidai.ui.scripts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.communitypoke.fdroidai.di.AppContainer
import org.communitypoke.fdroidai.ui.common.EmptyView
import org.communitypoke.fdroidai.ui.common.ErrorView
import org.communitypoke.fdroidai.ui.common.LoadingView
import org.communitypoke.fdroidai.userscript.Userscript

@Composable
fun UserscriptsScreen(
    container: AppContainer,
    onNewScript: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    val viewModel: UserscriptsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                UserscriptsViewModel(container.userscriptStore, container.userscriptRuntime)
            }
        },
    )
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Userscripts", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Run Greasemonkey-style scripts in the in-app browser",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenBrowser) {
                Icon(Icons.Filled.Public, contentDescription = "Open script browser")
            }
            IconButton(onClick = onNewScript) {
                Icon(Icons.Filled.Add, contentDescription = "New userscript")
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            state.loading -> LoadingView("Loading scripts…")
            state.error != null -> ErrorView(
                message = state.error ?: "Could not load scripts",
                onRetry = viewModel::refresh,
            )
            state.scripts.isEmpty() -> EmptyView(
                title = "No userscripts yet",
                message = "Tap + to generate one with AI or paste your own, " +
                    "then run it in the built-in browser.",
                icon = Icons.Filled.Code,
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.scripts, key = { it.id }) { script ->
                    ScriptRow(
                        script = script,
                        onToggle = { viewModel.setEnabled(script.id, it) },
                        onDelete = { viewModel.delete(script.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScriptRow(
    script: Userscript,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    script.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (script.metadata.description.isNotBlank()) {
                    Text(
                        script.metadata.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val scope = when {
                    script.metadata.matches.isNotEmpty() ->
                        script.metadata.matches.joinToString("  ")
                    else -> script.metadata.includes.joinToString("  ")
                }
                Text(
                    scope,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = script.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete ${script.name}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
