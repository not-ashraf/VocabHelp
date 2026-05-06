package com.example.vocabhelp.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vocabhelp.data.repo.SaveOutcome
import com.example.vocabhelp.data.repo.WordRepository
import com.example.vocabhelp.work.WorkScheduler
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

    fun setWord(value: String) = _state.update { it.copy(word = value) }
    fun setContext(value: String) = _state.update { it.copy(contextSentence = value) }
    fun setNote(value: String) = _state.update { it.copy(personalNote = value) }

    fun save(onDone: (SaveOutcome) -> Unit) {
        val current = _state.value
        if (current.word.isBlank() || current.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val outcome = repo.saveAndLookup(
                word = current.word,
                contextSentence = current.contextSentence,
                personalNote = current.personalNote,
            )
            if (outcome is SaveOutcome.Pending) {
                scheduler.enqueuePendingLookup()
            }
            _state.update { it.copy(saving = false) }
            onDone(outcome)
        }
    }
}

data class AddWordUiState(
    val word: String = "",
    val contextSentence: String = "",
    val personalNote: String = "",
    val saving: Boolean = false,
)
