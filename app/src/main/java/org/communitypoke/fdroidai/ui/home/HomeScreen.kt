package org.communitypoke.fdroidai.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.communitypoke.fdroidai.di.AppContainer
import org.communitypoke.fdroidai.ui.common.AppListItem
import org.communitypoke.fdroidai.ui.common.EmptyView
import org.communitypoke.fdroidai.ui.common.ErrorView
import org.communitypoke.fdroidai.ui.common.LoadingView
import org.communitypoke.fdroidai.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onAppClick: (String) -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.fdroidRepository, container.browseCategoryFilter) }
        },
    )
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("F-Droid AI") },
            actions = {
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh index")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        when (val s = state) {
            is UiState.Loading -> LoadingView("Fetching the F-Droid index…")
            is UiState.Error -> ErrorView(message = s.message, onRetry = { viewModel.load() })
            is UiState.Success -> {
                val data = s.data
                CategoryFilterRow(
                    categories = data.categories,
                    selected = data.selectedCategory,
                    onSelect = { container.browseCategoryFilter.value = it },
                )
                if (data.fromCache) {
                    Text(
                        text = "Showing cached index (offline)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (data.apps.isEmpty()) {
                    EmptyView(
                        title = "No apps here",
                        message = data.selectedCategory?.let { "Nothing found in \"$it\"." }
                            ?: "The repository returned no packages.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(data.apps, key = { it.packageName }) { app ->
                            AppListItem(app = app, onClick = { onAppClick(app.packageName) })
                        }
                        item {
                            Text(
                                text = "${data.totalApps} apps in this view · " +
                                    "use Search to find the rest",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<Pair<String, Int>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    if (categories.isEmpty()) return
    Box(Modifier.fillMaxWidth()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selected == null,
                    onClick = { onSelect(null) },
                    label = { Text("All") },
                )
            }
            items(categories.take(20), key = { it.first }) { (category, count) ->
                FilterChip(
                    selected = selected == category,
                    onClick = { onSelect(if (selected == category) null else category) },
                    label = { Text("$category ($count)") },
                )
            }
        }
    }
}
