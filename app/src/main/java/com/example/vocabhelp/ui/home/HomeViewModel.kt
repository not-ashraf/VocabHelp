package com.example.vocabhelp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vocabhelp.data.db.WordEntity
import com.example.vocabhelp.data.repo.WordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(
    private val repo: WordRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    private val words = _query
        .debounce(250)
        .flatMapLatest { repo.observeAll(it.trim()) }

    private val dueCount = repo.countDue()

    val state: StateFlow<HomeUiState> = combine(
        words,
        dueCount,
        _query,
        _selectedIds,
    ) { list, due, q, selected ->
        HomeUiState(words = list, dueCount = due, query = q, selectedIds = selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(q: String) {
        _query.value = q
        // Selection on a now-hidden row would be confusing; clear when filter changes.
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(id: Long) {
        _selectedIds.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repo.deleteByIds(ids)
            _selectedIds.value = emptySet()
        }
    }
}

data class HomeUiState(
    val words: List<WordEntity> = emptyList(),
    val dueCount: Int = 0,
    val query: String = "",
    val selectedIds: Set<Long> = emptySet(),
) {
    val inSelection: Boolean get() = selectedIds.isNotEmpty()
}
