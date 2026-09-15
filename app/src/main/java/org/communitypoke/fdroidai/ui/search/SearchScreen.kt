package org.communitypoke.fdroidai.ui.search

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.communitypoke.fdroidai.di.AppContainer
import org.communitypoke.fdroidai.ui.common.AppListItem
import org.communitypoke.fdroidai.ui.common.EmptyView
import org.communitypoke.fdroidai.ui.common.ErrorView
import org.communitypoke.fdroidai.ui.common.LoadingView

private val SUGGESTIONS = listOf(
    "password manager",
    "offline maps",
    "podcast player",
    "terminal emulator",
    "privacy browser",
    "markdown notes",
)

@Composable
fun SearchScreen(
    container: AppContainer,
    onAppClick: (String) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SearchViewModel(container.fdroidRepository, container.aiSearchProvider) }
        },
    )
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        SearchBar(
            query = state.query,
            aiMode = state.aiMode,
            onQueryChange = viewModel::onQueryChange,
            onToggleAi = { viewModel.setAiMode(!state.aiMode) },
            onSubmit = viewModel::submit,
        )
        Spacer(Modifier.height(8.dp))
        if (state.aiMode) {
            AiProviderBadge(
                providerName = viewModel.aiProviderName,
                isLive = viewModel.aiConfigured,
            )
            Spacer(Modifier.height(8.dp))
        }

        when {
            state.indexError != null -> ErrorView(
                message = state.indexError ?: "Index unavailable",
            )
            !state.indexReady -> LoadingView("Fetching the F-Droid index…")
            else -> SearchBody(state = state, viewModel = viewModel, onAppClick = onAppClick)
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    aiMode: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleAi: () -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(if (aiMode) "Describe what you need…" else "Search apps…") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
                IconButton(onClick = onToggleAi) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "Toggle AI search",
                        tint = if (aiMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
    )
}

@Composable
private fun AiProviderBadge(providerName: String, isLive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isLive) "Live AI · $providerName"
            else "Demo mode · $providerName (set AI_API_KEY for live results)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SearchBody(
    state: SearchUiState,
    viewModel: SearchViewModel,
    onAppClick: (String) -> Unit,
) {
    if (state.query.isBlank()) {
        Text(
            "Try one of these:",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SUGGESTIONS) { suggestion ->
                AssistChip(
                    onClick = {
                        viewModel.onQueryChange(suggestion)
                        viewModel.submit()
                    },
                    label = { Text(suggestion) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        EmptyView(
            title = "Search F-Droid",
            message = "Type keywords, or toggle the sparkle icon to describe what you want in plain language.",
        )
        return
    }

    if (state.aiMode) {
        AiResults(state = state, onAppClick = onAppClick)
    } else {
        when {
            state.searching -> LoadingView("Searching…")
            state.results.isEmpty() -> EmptyView(
                title = "No matches",
                message = "Nothing matched \"${state.query}\". Try AI search for semantic results.",
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.results, key = { it.packageName }) { app ->
                    AppListItem(app = app, onClick = { onAppClick(app.packageName) })
                }
            }
        }
    }
}

@Composable
private fun AiResults(state: SearchUiState, onAppClick: (String) -> Unit) {
    when {
        state.aiSearching -> LoadingView("Thinking…")
        state.aiError != null -> ErrorView(
            message = state.aiError ?: "AI search failed",
        )
        state.aiResponse == null -> EmptyView(
            title = "AI search ready",
            message = "Submit the query and the AI will interpret it.",
        )
        state.aiResponse.results.isEmpty() -> EmptyView(
            title = "No semantic matches",
            message = state.aiResponse.summary,
        )
        else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            state.aiResponse.summary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (state.aiResponse.isFallback) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "via ${state.aiResponse.providerName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(
                state.aiResponse.results,
                key = { it.app.packageName },
            ) { ranked ->
                AppListItem(
                    app = ranked.app,
                    onClick = { onAppClick(ranked.app.packageName) },
                    subtitle = buildString {
                        append(ranked.app.summary.ifBlank { ranked.app.packageName })
                        if (ranked.reason.isNotBlank()) {
                            append("\nAI: ")
                            append(ranked.reason)
                        }
                    },
                )
            }
        }
    }
}
