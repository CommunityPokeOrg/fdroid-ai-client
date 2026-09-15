package org.communitypoke.fdroidai.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.communitypoke.fdroidai.ai.AiSearchProvider
import org.communitypoke.fdroidai.ai.AiSearchRequest
import org.communitypoke.fdroidai.ai.AiSearchResponse
import org.communitypoke.fdroidai.data.fdroid.FdroidRepository
import org.communitypoke.fdroidai.data.model.FdroidApp
import org.communitypoke.fdroidai.data.search.SearchEngine

data class SearchUiState(
    val indexReady: Boolean = false,
    val indexError: String? = null,
    val query: String = "",
    val aiMode: Boolean = false,
    val results: List<FdroidApp> = emptyList(),
    val searching: Boolean = false,
    val aiResponse: AiSearchResponse? = null,
    val aiSearching: Boolean = false,
    val aiError: String? = null,
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val repository: FdroidRepository,
    private val aiProvider: AiSearchProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            try {
                repository.getSnapshot()
                _state.update { it.copy(indexReady = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(indexError = e.message ?: "Could not load the F-Droid index.")
                }
            }
        }
        viewModelScope.launch {
            queryFlow.debounce(250).collect { runLocalSearch(it) }
        }
    }

    fun onQueryChange(query: String) {
        queryFlow.value = query
        _state.update {
            it.copy(
                query = query,
                aiResponse = if (it.aiMode) it.aiResponse else null,
                aiError = null,
            )
        }
    }

    fun setAiMode(enabled: Boolean) {
        _state.update { it.copy(aiMode = enabled, aiError = null) }
        if (enabled && _state.value.query.isNotBlank()) runAiSearch()
    }

    fun submit() {
        if (_state.value.aiMode) {
            runAiSearch()
        } else {
            viewModelScope.launch { runLocalSearch(_state.value.query) }
        }
    }

    private suspend fun runLocalSearch(query: String) {
        if (query.isBlank()) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        if (!_state.value.indexReady) return
        _state.update { it.copy(searching = true) }
        val apps = repository.getSnapshot().apps
        val results = SearchEngine.search(query, apps)
        _state.update { it.copy(results = results, searching = false) }
    }

    fun runAiSearch() {
        val query = _state.value.query.trim()
        if (query.isEmpty() || !_state.value.indexReady) return
        _state.update { it.copy(aiSearching = true, aiError = null) }
        viewModelScope.launch {
            try {
                val apps = repository.getSnapshot().apps
                val response = aiProvider.search(
                    AiSearchRequest(query = query, apps = apps),
                )
                _state.update { it.copy(aiResponse = response, aiSearching = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(aiSearching = false, aiError = e.message ?: "AI search failed.")
                }
            }
        }
    }

    val aiProviderName: String get() = aiProvider.name
    val aiConfigured: Boolean get() = aiProvider.isConfigured
}
