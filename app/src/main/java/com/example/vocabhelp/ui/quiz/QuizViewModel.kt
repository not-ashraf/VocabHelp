package com.example.vocabhelp.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vocabhelp.data.db.WordEntity
import com.example.vocabhelp.data.repo.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QuizViewModel(
    private val repo: WordRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    init { loadNext() }

    fun loadNext() {
        _state.value = QuizUiState.Loading
        viewModelScope.launch {
            val q = repo.pickQuizQuestion()
            if (q == null) {
                _state.value = QuizUiState.Done
                return@launch
            }
            val options = buildOptions(q.target, q.distractors)
            val correctIndex = options.indexOfFirst { it.id == q.target.id }
            _state.value = QuizUiState.Question(
                target = q.target,
                options = options,
                correctIndex = correctIndex,
                selectedIndex = null,
            )
        }
    }

    fun answer(index: Int) {
        val current = _state.value as? QuizUiState.Question ?: return
        if (current.selectedIndex != null) return
        _state.update { current.copy(selectedIndex = index) }
        val correct = index == current.correctIndex
        viewModelScope.launch {
            repo.applyReview(current.target.id, quality = if (correct) 5 else 1)
        }
    }

    private fun buildOptions(target: WordEntity, distractors: List<WordEntity>): List<QuizOption> {
        val real = (listOf(target) + distractors).map { w ->
            // Each word may have multiple saved senses; pick one at random per session.
            // For the target this rotates which sense is being tested over time.
            val sense = w.meanings.randomOrNull()?.definition.orEmpty()
            QuizOption(w.id, sense)
        }
        val padded = if (real.size < 4) {
            real + FALLBACK_DISTRACTORS
                .filterNot { fb -> real.any { it.text == fb } }
                .take(4 - real.size)
                .map { QuizOption(id = -1L, text = it) }
        } else {
            real
        }
        return padded.shuffled()
    }

    private companion object {
        val FALLBACK_DISTRACTORS = listOf(
            "a small alcove or recess",
            "a sharp criticism",
            "a state of tranquility",
            "a long, narrow strip of land",
            "a feeling of intense longing",
            "a brief, witty remark",
            "a dim, indistinct figure",
            "a strict adherence to rules",
        )
    }
}

sealed interface QuizUiState {
    data object Loading : QuizUiState
    data object Done : QuizUiState
    data class Question(
        val target: WordEntity,
        val options: List<QuizOption>,
        val correctIndex: Int,
        val selectedIndex: Int?,
    ) : QuizUiState
}

data class QuizOption(val id: Long, val text: String)
