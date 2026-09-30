package com.example.vocabhelp.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vocabhelp.data.db.WordEntity
import com.example.vocabhelp.data.repo.RelatedWord
import com.example.vocabhelp.data.repo.WordRepository
import com.example.vocabhelp.quiz.ChoiceCard
import com.example.vocabhelp.quiz.FlashCard
import com.example.vocabhelp.quiz.QuizCard
import com.example.vocabhelp.quiz.QuizQuestionFactory
import com.example.vocabhelp.quiz.QuizType
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

class QuizViewModel(
    private val repo: WordRepository,
    private val random: Random = Random.Default,
) : ViewModel() {

    private val _state = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    // Confined to the main thread (viewModelScope), so a plain map is safe.
    private val relatedCache = mutableMapOf<String, List<RelatedWord>>()
    private val factory = QuizQuestionFactory(related = ::cachedRelated, random = random)

    private var session: List<WordEntity> = emptyList()
    private var pool: List<WordEntity> = emptyList()
    private var index = 0
    private var correctCount = 0
    private var next: Deferred<QuizCard>? = null
    private val pendingReviews = mutableListOf<Job>()

    init {
        viewModelScope.launch {
            // Pick the session once, up front: shuffled so it doesn't always open on the
            // same most-overdue word, and immune to the DB changing as answers are saved.
            session = repo.getDueWords().shuffled(random).take(SESSION_SIZE)
            pool = repo.getReviewableWords()
            if (session.isEmpty()) {
                _state.value = QuizUiState.Done(answered = 0, correct = 0, remainingDue = 0)
            } else {
                show(buildCard(0, avoid = null))
            }
        }
    }

    /** Multiple-choice answer. Correct = SM-2 "good" (4): recognising isn't perfect recall. */
    fun answer(optionIndex: Int) {
        val q = _state.value as? QuizUiState.Question ?: return
        val card = q.card as? ChoiceCard ?: return
        if (q.selectedIndex != null) return
        val correct = optionIndex == card.correctIndex
        if (correct) correctCount++
        _state.value = q.copy(selectedIndex = optionIndex)
        review(card.target, if (correct) QUALITY_GOOD else QUALITY_WRONG)
    }

    fun reveal() {
        val q = _state.value as? QuizUiState.Question ?: return
        if (q.card is FlashCard) _state.value = q.copy(revealed = true)
    }

    /** Flashcard self-grade; moves straight on to the next card. */
    fun grade(grade: RecallGrade) {
        val q = _state.value as? QuizUiState.Question ?: return
        if (q.card !is FlashCard || !q.revealed) return
        if (grade != RecallGrade.FORGOT) correctCount++
        review(q.card.target, grade.quality)
        loadNext()
    }

    fun loadNext() {
        val current = _state.value as? QuizUiState.Question ?: return
        index++
        _state.value = QuizUiState.Loading
        viewModelScope.launch {
            if (index >= session.size) {
                pendingReviews.joinAll()
                _state.value = QuizUiState.Done(
                    answered = session.size,
                    correct = correctCount,
                    remainingDue = repo.getDueWords().size,
                )
                return@launch
            }
            show(next?.await() ?: buildCard(index, avoid = current.card.type))
        }
    }

    private fun show(card: QuizCard) {
        _state.value = QuizUiState.Question(card = card, number = index + 1, total = session.size)
        // Build the following card while the user answers this one.
        val nextIndex = index + 1
        next = if (nextIndex < session.size) {
            viewModelScope.async { buildCard(nextIndex, avoid = card.type) }
        } else {
            null
        }
    }

    private suspend fun buildCard(i: Int, avoid: QuizType?): QuizCard {
        val target = session[i]
        return factory.build(target, others = pool.filter { it.id != target.id }, avoid = avoid)
    }

    private fun review(word: WordEntity, quality: Int) {
        pendingReviews += viewModelScope.launch { repo.applyReview(word.id, quality) }
    }

    private suspend fun cachedRelated(word: String): List<RelatedWord> =
        relatedCache[word] ?: (withTimeoutOrNull(RELATED_TIMEOUT_MS) { repo.related(word) } ?: emptyList())
            .also { relatedCache[word] = it }

    private companion object {
        const val SESSION_SIZE = 10
        const val RELATED_TIMEOUT_MS = 2_500L
        const val QUALITY_GOOD = 4
        const val QUALITY_WRONG = 1
    }
}

/** Flashcard self-grades mapped onto SM-2's 0–5 quality scale. */
enum class RecallGrade(val label: String, val quality: Int) {
    FORGOT("Forgot", 1),
    HARD("Hard", 3),
    GOOD("Good", 4),
    EASY("Easy", 5),
}

sealed interface QuizUiState {
    data object Loading : QuizUiState
    data class Done(val answered: Int, val correct: Int, val remainingDue: Int) : QuizUiState
    data class Question(
        val card: QuizCard,
        val number: Int,
        val total: Int,
        /** Multiple choice: the tapped option, once answered. */
        val selectedIndex: Int? = null,
        /** Flashcard: meaning shown, awaiting a self-grade. */
        val revealed: Boolean = false,
    ) : QuizUiState {
        val answered: Boolean get() = selectedIndex != null
    }
}
