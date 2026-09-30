package com.example.vocabhelp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vocabhelp.data.db.WordEntity
import com.example.vocabhelp.data.prefs.SettingsRepository
import com.example.vocabhelp.data.repo.WordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(
    private val repo: WordRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    private val words = _query
        .debounce(250)
        .flatMapLatest { repo.observeAll(it.trim()) }

    private val suggestions = _query
        .debounce(250)
        .map { it.trim().lowercase() }
        .distinctUntilChanged()
        .mapLatest { q ->
            if (q.length < MIN_SUGGEST_LENGTH) emptyList() else repo.suggestNewWords(q)
        }

    private val dueCount = repo.countDue()

    private val searchResults = combine(words, suggestions, ::Pair)

    val state: StateFlow<HomeUiState> = combine(
        searchResults,
        dueCount,
        _query,
        _selectedIds,
        settings.notificationPromptDismissed,
    ) { (list, suggested), due, q, selected, dismissed ->
        HomeUiState(
            words = list,
            suggestions = suggested,
            dueCount = due,
            query = q,
            selectedIds = selected,
            notificationPromptDismissed = dismissed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(q: String) {
        _query.value = q
        // Selection on a now-hidden row would be confusing; clear when filter changes.
        _selectedIds.value = emptySet()
    }

    /**
     * Keyboard "search" action: open the saved word if it exists, otherwise
     * start adding it. Queries the DB directly so it isn't subject to debounce.
     */
    fun submitQuery(onOpenSaved: (Long) -> Unit, onAddNew: (String) -> Unit) {
        val q = _query.value.trim().lowercase()
        if (q.isEmpty()) return
        viewModelScope.launch {
            val existing = repo.findByWord(q)
            if (existing != null) onOpenSaved(existing.id) else onAddNew(q)
        }
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

    /** Records that we've prompted the user about notifications; suppresses the Home card. */
    fun dismissNotificationPrompt() {
        viewModelScope.launch { settings.setNotificationPromptDismissed(true) }
    }

    private companion object {
        const val MIN_SUGGEST_LENGTH = 2
    }
}

data class HomeUiState(
    val words: List<WordEntity> = emptyList(),
    /** Dictionary autocomplete for [query], excluding words already saved. */
    val suggestions: List<String> = emptyList(),
    val dueCount: Int = 0,
    val query: String = "",
    val selectedIds: Set<Long> = emptySet(),
    val notificationPromptDismissed: Boolean = false,
) {
    val inSelection: Boolean get() = selectedIds.isNotEmpty()

    /**
     * New words offered for adding: autocomplete suggestions, then the typed
     * query itself last (so rare words Datamuse doesn't know are still addable).
     */
    val addCandidates: List<String>
        get() {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return emptyList()
            val typedIsSaved = words.any { it.word == q }
            return if (typedIsSaved || q in suggestions) suggestions else suggestions + q
        }
}
