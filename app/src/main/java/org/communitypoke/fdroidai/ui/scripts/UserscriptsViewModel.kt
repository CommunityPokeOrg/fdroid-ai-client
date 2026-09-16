package org.communitypoke.fdroidai.ui.scripts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.communitypoke.fdroidai.userscript.Userscript
import org.communitypoke.fdroidai.userscript.UserscriptRuntime
import org.communitypoke.fdroidai.userscript.UserscriptStore

data class UserscriptsUiState(
    val loading: Boolean = true,
    val scripts: List<Userscript> = emptyList(),
    val error: String? = null,
)

class UserscriptsViewModel(
    private val store: UserscriptStore,
    private val runtime: UserscriptRuntime,
) : ViewModel() {

    private val _state = MutableStateFlow(UserscriptsUiState())
    val state: StateFlow<UserscriptsUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val scripts = store.list()
                runtime.setScripts(scripts)
                _state.update { it.copy(scripts = scripts, loading = false, error = null) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: "Could not load userscripts.")
                }
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            store.setEnabled(id, enabled)
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            store.delete(id)
            refresh()
        }
    }
}
