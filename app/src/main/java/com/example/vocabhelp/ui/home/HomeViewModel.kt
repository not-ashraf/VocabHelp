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

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(
    private val repo: WordRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")

    private val words = _query
        .debounce(250)
        .flatMapLatest { repo.observeAll(it.trim()) }

    private val dueCount = repo.countDue()

    val state: StateFlow<HomeUiState> = combine(words, dueCount, _query) { list, due, q ->
        HomeUiState(words = list, dueCount = due, query = q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(q: String) {
        _query.value = q
    }
}

data class HomeUiState(
    val words: List<WordEntity> = emptyList(),
    val dueCount: Int = 0,
    val query: String = "",
)
