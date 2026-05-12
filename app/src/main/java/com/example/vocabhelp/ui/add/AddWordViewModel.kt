package com.example.vocabhelp.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vocabhelp.data.repo.DefinitionOption
import com.example.vocabhelp.data.repo.LookupOutcome
import com.example.vocabhelp.data.repo.SaveOutcome
import com.example.vocabhelp.data.repo.WordRepository
import com.example.vocabhelp.work.WorkScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddWordViewModel(
    private val repo: WordRepository,
    private val scheduler: WorkScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(AddWordUiState())
    val state: StateFlow<AddWordUiState> = _state.asStateFlow()

    private var lookupJob: Job? = null

    fun setWord(value: String) {
        _state.update {
            // Editing the word invalidates any previous lookup result.
            it.copy(
                word = value,
                lookup = LookupState.Idle,
                selectedIndex = null,
                manualMeaning = "",
            )
        }
        // Cancel any in-flight lookup tied to a stale word.
        lookupJob?.cancel()
    }

    fun setContext(value: String) = _state.update { it.copy(contextSentence = value) }
    fun setNote(value: String) = _state.update { it.copy(personalNote = value) }
    fun setManualMeaning(value: String) = _state.update { it.copy(manualMeaning = value) }
    fun selectOption(index: Int) = _state.update { it.copy(selectedIndex = index) }

    /** Explicitly look up the current word. No-ops if the field is empty or a lookup is already running. */
    fun lookupNow() {
        val word = _state.value.word.trim().lowercase()
        if (word.isBlank()) return
        if (_state.value.lookup is LookupState.Loading) return
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch { runLookup(word) }
    }

    private suspend fun runLookup(word: String) {
        _state.update { it.copy(lookup = LookupState.Loading, selectedIndex = null) }
        val existing = repo.findByWord(word)
        if (existing != null) {
            _state.update { it.copy(lookup = LookupState.AlreadySaved(existing.id)) }
            return
        }
        val outcome = repo.lookup(word)
        _state.update { current ->
            // Only apply if the user hasn't edited the word in the meantime.
            if (current.word.trim().lowercase() != word) return@update current
            current.copy(
                lookup = when (outcome) {
                    is LookupOutcome.Success -> LookupState.Found(outcome.options, outcome.audioUrl)
                    LookupOutcome.NotFound -> LookupState.NotFound
                    LookupOutcome.NetworkError -> LookupState.NetworkError
                },
                selectedIndex = null,
            )
        }
    }

    fun save(onDone: (SaveOutcome) -> Unit) {
        val current = _state.value
        if (current.word.isBlank() || current.saving) return

        val (selected, audio) = when (val l = current.lookup) {
            is LookupState.Found -> l.options.getOrNull(current.selectedIndex ?: -1) to l.audioUrl
            else -> null to null
        }
        val manual = current.manualMeaning.takeIf {
            it.isNotBlank() && current.lookup !is LookupState.Found
        }

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val outcome = repo.save(
                word = current.word,
                selected = selected,
                audioUrl = audio,
                manualMeaning = manual,
                contextSentence = current.contextSentence,
                personalNote = current.personalNote,
            )
            if (outcome is SaveOutcome.Pending) scheduler.enqueuePendingLookup()
            _state.update { it.copy(saving = false) }
            onDone(outcome)
        }
    }
}

data class AddWordUiState(
    val word: String = "",
    val contextSentence: String = "",
    val personalNote: String = "",
    val manualMeaning: String = "",
    val lookup: LookupState = LookupState.Idle,
    val selectedIndex: Int? = null,
    val saving: Boolean = false,
) {
    val canLookup: Boolean
        get() = word.isNotBlank() && lookup !is LookupState.Loading

    val canSave: Boolean
        get() = !saving && word.isNotBlank() && when (lookup) {
            is LookupState.Found -> selectedIndex != null
            LookupState.NotFound,
            LookupState.NetworkError,
            -> true
            LookupState.Loading,
            LookupState.Idle,
            -> false
            is LookupState.AlreadySaved -> false
        }
}

sealed interface LookupState {
    data object Idle : LookupState
    data object Loading : LookupState
    data class Found(val options: List<DefinitionOption>, val audioUrl: String?) : LookupState
    data object NotFound : LookupState
    data object NetworkError : LookupState
    data class AlreadySaved(val id: Long) : LookupState
}
