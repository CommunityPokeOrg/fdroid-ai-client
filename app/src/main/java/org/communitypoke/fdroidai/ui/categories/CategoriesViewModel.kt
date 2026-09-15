package org.communitypoke.fdroidai.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.communitypoke.fdroidai.data.fdroid.FdroidRepository
import org.communitypoke.fdroidai.ui.common.UiState

class CategoriesViewModel(private val repository: FdroidRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Pair<String, Int>>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Pair<String, Int>>>> = _state

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val snap = repository.getSnapshot()
                UiState.Success(
                    snap.byCategory.entries
                        .sortedByDescending { it.value.size }
                        .map { it.key to it.value.size },
                )
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Failed to load categories.")
            }
        }
    }
}
