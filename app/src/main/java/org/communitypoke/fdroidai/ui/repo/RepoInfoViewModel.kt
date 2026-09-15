package org.communitypoke.fdroidai.ui.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.communitypoke.fdroidai.data.fdroid.FdroidRepository
import org.communitypoke.fdroidai.data.model.RepoMetadata
import org.communitypoke.fdroidai.ui.common.UiState

data class RepoUiModel(val metadata: RepoMetadata, val fromCache: Boolean)

class RepoInfoViewModel(private val repository: FdroidRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<RepoUiModel>>(UiState.Loading)
    val state: StateFlow<UiState<RepoUiModel>> = _state

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val snap = repository.getSnapshot()
                UiState.Success(RepoUiModel(snap.metadata, snap.fromCache))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Failed to load repository metadata.")
            }
        }
    }
}
