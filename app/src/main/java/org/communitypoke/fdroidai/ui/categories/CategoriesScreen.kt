package org.communitypoke.fdroidai.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.communitypoke.fdroidai.di.AppContainer
import org.communitypoke.fdroidai.ui.common.EmptyView
import org.communitypoke.fdroidai.ui.common.ErrorView
import org.communitypoke.fdroidai.ui.common.LoadingView
import org.communitypoke.fdroidai.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    container: AppContainer,
    onCategoryClick: (String) -> Unit,
) {
    val viewModel: CategoriesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CategoriesViewModel(container.fdroidRepository) }
        },
    )
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Categories") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
        when (val s = state) {
            is UiState.Loading -> LoadingView("Counting categories…")
            is UiState.Error -> ErrorView(message = s.message, onRetry = { viewModel.load() })
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    EmptyView(title = "No categories", message = "The index lists no categories.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(s.data, key = { it.first }) { (category, count) ->
                            CategoryCard(
                                category = category,
                                count = count,
                                onClick = { onCategoryClick(category) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(category: String, count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(category, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "$count apps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
