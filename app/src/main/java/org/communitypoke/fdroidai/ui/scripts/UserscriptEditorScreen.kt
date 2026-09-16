package org.communitypoke.fdroidai.ui.scripts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.communitypoke.fdroidai.di.AppContainer

@Composable
fun UserscriptEditorScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: UserscriptEditorViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                UserscriptEditorViewModel(
                    builder = container.userscriptBuilder,
                    store = container.userscriptStore,
                    runtime = container.userscriptRuntime,
                    registry = container.agentRegistry,
                )
            }
        },
    )
    val state by viewModel.state.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("New userscript", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))

        Text(
            "Describe what the script should do",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = state.prompt,
            onValueChange = viewModel::onPromptChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("e.g. Dark theme for wikipedia.org") },
        )
        Spacer(Modifier.height(8.dp))

        Text("Agent SDK", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            viewModel.providers.forEach { provider ->
                FilterChip(
                    selected = selectedProvider == provider,
                    onClick = { viewModel.selectProvider(provider) },
                    label = {
                        Text(
                            provider.name.substringBefore(" (") +
                                if (!provider.isConfigured) " · no key" else "",
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = viewModel::generate,
            enabled = !state.generating && state.prompt.isNotBlank(),
        ) {
            if (state.generating) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            Text(if (state.generating) "Generating…" else "Generate with ${selectedProvider.name.substringBefore(" (")}")
        }
        state.generateError?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        state.draftProvider?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                "Generated by $it" + if (state.draftIsDemo) " — demo mode (set CLAUDE_API_KEY or CODEX_API_KEY for live agents)" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))

        Text("Source", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = state.source,
            onValueChange = viewModel::onSourceChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 12,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            placeholder = { Text("// ==UserScript==\n// @name ...") },
        )
        if (state.errors.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            state.errors.forEach { error ->
                Text(
                    "• $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            OutlinedButton(onClick = onBack) { Text("Cancel") }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = viewModel::save,
                enabled = state.source.isNotBlank() && !state.saving,
            ) {
                Text(if (state.saving) "Saving…" else "Save script")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
