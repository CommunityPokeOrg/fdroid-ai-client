package org.communitypoke.fdroidai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.communitypoke.fdroidai.data.fdroid.FdroidRepository
import org.communitypoke.fdroidai.data.model.FdroidApp
import org.communitypoke.fdroidai.ui.common.UiState

data class HomeUiModel(
    val apps: List<FdroidApp>,
    val categories: List<Pair<String, Int>>,
    val selectedCategory: String?,
    val totalApps: Int,
    val fromCache: Boolean,
)

class HomeViewModel(
    private val repository: FdroidRepository,
    categoryFilter: MutableStateFlow<String?>,
) : ViewModel() {

    private val snapshotState = MutableStateFlow<UiState<FdroidRepository.RepoSnapshot>>(UiState.Loading)

    val state: StateFlow<UiState<HomeUiModel>> =
        combine(snapshotState, categoryFilter) { snap, category ->
            when (snap) {
                is UiState.Loading -> UiState.Loading
                is UiState.Error -> UiState.Error(snap.message)
                is UiState.Success -> {
                    val s = snap.data
                    val filtered = if (category == null) s.apps else (s.byCategory[category] ?: emptyList())
                    UiState.Success(
                        HomeUiModel(
                            apps = filtered.take(MAX_LISTED),
                            categories = s.byCategory.entries
                                .sortedByDescending { it.value.size }
                                .map { it.key to it.value.size },
                            selectedCategory = category,
                            totalApps = filtered.size,
                            fromCache = s.fromCache,
                        )
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        load()
    }

    fun load() {
        snapshotState.value = UiState.Loading
        viewModelScope.launch {
            snapshotState.value = try {
                UiState.Success(repository.getSnapshot())
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Failed to load the F-Droid index.")
            }
        }
    }

    fun refresh() {
        snapshotState.value = UiState.Loading
        viewModelScope.launch {
            snapshotState.value = try {
                UiState.Success(repository.refresh())
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Refresh failed.")
            }
        }
    }

    private companion object {
        const val MAX_LISTED = 400
    }
}
