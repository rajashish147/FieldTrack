package com.fieldtrack.app.ui

/**
 * Generic sealed class representing the three possible UI states for any
 * data-loading operation.
 *
 * Designed to be held in a single LiveData<UiState<T>> instead of three
 * separate LiveData fields (data / loading / error). The [when] expression
 * in the observer is exhaustive by construction — no `else` branch needed.
 *
 * Usage in ViewModel:
 *   private val _items = MutableLiveData<UiState<List<Item>>>()
 *   val items: LiveData<UiState<List<Item>>> = _items
 *
 *   fun loadItems() {
 *       _items.value = UiState.Loading
 *       viewModelScope.launch {
 *           when (val result = repository.getItems()) {
 *               is NetworkResult.Success -> _items.value = UiState.Success(result.data)
 *               is NetworkResult.Error   -> _items.value = UiState.Error(result.message)
 *               is NetworkResult.Loading -> Unit
 *           }
 *       }
 *   }
 *
 * Usage in Fragment:
 *   viewModel.items.observe(viewLifecycleOwner) { state ->
 *       when (state) {
 *           is UiState.Loading        -> showProgress()
 *           is UiState.Success        -> showData(state.data)
 *           is UiState.Error          -> showError(state.message)
 *       }
 *   }
 *
 * @param T The type of data carried by [Success].
 */
sealed class UiState<out T> {

    /**
     * A request is in flight.
     * The UI should show a progress indicator and disable interactive elements.
     */
    data object Loading : UiState<Nothing>()

    /**
     * The request completed successfully.
     *
     * @param data The result payload. For list endpoints this may be an empty
     *             list — that is a valid success, not an error.
     */
    data class Success<out T>(val data: T) : UiState<T>()

    /**
     * The request failed.
     *
     * @param message A user-facing description of what went wrong.
     *                Sourced from [NetworkResult.Error.message], which maps
     *                SQLSTATE codes to human-readable strings in the repository.
     */
    data class Error(val message: String) : UiState<Nothing>()
}
