package org.communitypoke.fdroidai.ui.common

/** Generic async UI state for screens that load a single resource. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val canRetry: Boolean = true) : UiState<Nothing>
}
