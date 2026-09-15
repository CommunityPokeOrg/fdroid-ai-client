package org.communitypoke.fdroidai.ui.repo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.communitypoke.fdroidai.di.AppContainer
import org.communitypoke.fdroidai.ui.common.ErrorView
import org.communitypoke.fdroidai.ui.common.LoadingView
import org.communitypoke.fdroidai.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoInfoScreen(container: AppContainer) {
    val viewModel: RepoInfoViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RepoInfoViewModel(container.fdroidRepository) }
        },
    )
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Repository") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
        when (val s = state) {
            is UiState.Loading -> LoadingView("Reading repo metadata…")
            is UiState.Error -> ErrorView(message = s.message, onRetry = { viewModel.load() })
            is UiState.Success -> RepoContent(model = s.data)
        }
    }
}

@Composable
private fun RepoContent(model: RepoUiModel) {
    val repo = model.metadata
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text(repo.name, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    repo.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        InfoRow("Apps in index", "%,d".format(repo.appCount))
        if (repo.timestamp > 0) {
            val stamp = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US)
                .format(Date(repo.timestamp))
            InfoRow("Index updated", stamp)
        }
        InfoRow("Source", if (model.fromCache) "Cached (offline)" else "Live download")

        if (repo.description.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                repo.description.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (repo.mirrors.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Mirrors", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            repo.mirrors.forEach { mirror ->
                Text(
                    mirror,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
