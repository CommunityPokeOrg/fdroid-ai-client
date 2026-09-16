package org.communitypoke.fdroidai.ui.scripts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.communitypoke.fdroidai.ai.agent.AgentProviderRegistry
import org.communitypoke.fdroidai.ai.agent.AgentSdkProvider
import org.communitypoke.fdroidai.userscript.UserscriptBuilder
import org.communitypoke.fdroidai.userscript.UserscriptParseException
import org.communitypoke.fdroidai.userscript.UserscriptRuntime
import org.communitypoke.fdroidai.userscript.UserscriptStore

data class EditorUiState(
    val prompt: String = "",
    val source: String = "",
    val generating: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val draftProvider: String? = null,
    val draftIsDemo: Boolean = false,
    val generateError: String? = null,
    val errors: List<String> = emptyList(),
)

class UserscriptEditorViewModel(
    private val builder: UserscriptBuilder,
    private val store: UserscriptStore,
    private val runtime: UserscriptRuntime,
    val registry: AgentProviderRegistry,
) : ViewModel() {

    val providers: List<AgentSdkProvider> = registry.providers

    private val _selectedProvider = MutableStateFlow(registry.preferred())
    val selectedProvider: StateFlow<AgentSdkProvider> = _selectedProvider

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state

    fun selectProvider(provider: AgentSdkProvider) {
        _selectedProvider.value = provider
    }

    fun onPromptChange(prompt: String) {
        _state.update { it.copy(prompt = prompt, generateError = null) }
    }

    fun onSourceChange(source: String) {
        _state.update { it.copy(source = source, errors = emptyList(), saved = false) }
    }

    fun generate() {
        val prompt = _state.value.prompt.trim()
        if (prompt.isEmpty() || _state.value.generating) return
        _state.update { it.copy(generating = true, generateError = null) }
        viewModelScope.launch {
            try {
                val draft = builder.build(prompt, _selectedProvider.value)
                _state.update {
                    it.copy(
                        generating = false,
                        source = draft.source,
                        draftProvider = draft.providerName,
                        draftIsDemo = draft.isDemo,
                        errors = emptyList(),
                        saved = false,
                    )
                }
            } catch (e: UserscriptParseException) {
                _state.update {
                    it.copy(
                        generating = false,
                        source = e.partialSource ?: it.source,
                        errors = e.errors,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        generating = false,
                        generateError = e.message ?: "Generation failed.",
                    )
                }
            }
        }
    }

    fun save() {
        if (_state.value.saving || _state.value.source.isBlank()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val script = builder.toUserscript(_state.value.source)
                store.save(script)
                runtime.setScripts(store.list())
                _state.update { it.copy(saving = false, saved = true, errors = emptyList()) }
            } catch (e: UserscriptParseException) {
                _state.update { it.copy(saving = false, errors = e.errors) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(saving = false, errors = listOf(e.message ?: "Save failed."))
                }
            }
        }
    }
}
