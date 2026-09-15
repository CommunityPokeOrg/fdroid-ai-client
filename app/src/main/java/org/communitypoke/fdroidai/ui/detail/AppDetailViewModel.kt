package org.communitypoke.fdroidai.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.communitypoke.fdroidai.data.fdroid.FdroidRepository
import org.communitypoke.fdroidai.data.model.FdroidApp
import org.communitypoke.fdroidai.ui.common.UiState

class AppDetailViewModel(
    private val repository: FdroidRepository,
    private val packageName: String,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<FdroidApp>>(UiState.Loading)
    val state: StateFlow<UiState<FdroidApp>> = _state

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val app = repository.getApp(packageName)
                if (app != null) {
                    UiState.Success(app)
                } else {
                    UiState.Error("Package \"$packageName\" was not found in the index.", canRetry = false)
                }
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Failed to load app details.")
            }
        }
    }
}
