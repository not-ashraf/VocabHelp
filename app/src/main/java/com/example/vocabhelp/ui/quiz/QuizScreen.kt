package com.example.vocabhelp.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vocabhelp.data.db.WordEntity
import com.example.vocabhelp.quiz.ChoiceCard
import com.example.vocabhelp.quiz.FlashCard
import com.example.vocabhelp.quiz.QuizType
import com.example.vocabhelp.ui.common.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(onBack: () -> Unit) {
    val vm = appViewModel { QuizViewModel(it.wordRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Quiz") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        (state as? QuizUiState.Question)?.let { q ->
                            Text(
                                "${q.number} / ${q.total}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(end = 16.dp),
                            )
                        }
                    },
                )
                (state as? QuizUiState.Question)?.let { q ->
                    LinearProgressIndicator(
                        progress = { (q.number - 1).toFloat() / q.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val s = state) {
                QuizUiState.Loading -> CircularProgressIndicator(Modifier.padding(top = 48.dp))
                is QuizUiState.Done -> DoneState(s, onBack = onBack)
                is QuizUiState.Question -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    when (val card = s.card) {
                        is ChoiceCard -> ChoiceContent(
                            card = card,
                            state = s,
                            onAnswer = vm::answer,
                            onNext = vm::loadNext,
                        )
                        is FlashCard -> FlashCardContent(
                            card = card,
                            revealed = s.revealed,
                            onReveal = vm::reveal,
                            onGrade = vm::grade,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceContent(
    card: ChoiceCard,
    state: QuizUiState.Question,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
) {
    val instruction = when (card.type) {
        QuizType.MEANING -> "What does this word mean?"
        QuizType.SYNONYM -> "Which word is closest in meaning?"
        QuizType.REVERSE -> "Which word matches this definition?"
        QuizType.FLASHCARD -> ""
    }
    Instruction(instruction)
    if (card.type == QuizType.REVERSE) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        ) {
            Text(
                text = card.prompt,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(20.dp),
            )
        }
    } else {
        PromptWord(card.prompt)
    }

    card.options.forEachIndexed { index, option ->
        OptionCard(
            text = option,
            isWord = card.type != QuizType.MEANING,
            visual = optionVisual(index, card.correctIndex, state.selectedIndex),
            onClick = { onAnswer(index) },
        )
        Spacer(Modifier.height(12.dp))
    }

    if (state.answered) {
        val correct = state.selectedIndex == card.correctIndex
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (correct) "Correct!" else "Not quite.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (correct) CorrectGreen else WrongRed,
        )
        Spacer(Modifier.height(12.dp))
        WordDetails(card.target)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.number == state.total) "Finish" else "Next")
        }
    }
}

@Composable
private fun FlashCardContent(
    card: FlashCard,
    revealed: Boolean,
    onReveal: () -> Unit,
    onGrade: (RecallGrade) -> Unit,
) {
    Instruction("Do you remember what this means?")
    PromptWord(card.target.word)
    if (!revealed) {
        Text(
            "Say the meaning to yourself, then check.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onReveal, modifier = Modifier.fillMaxWidth()) {
            Text("Show meaning")
        }
        return
    }
    WordDetails(card.target, showWord = false)
    Spacer(Modifier.height(24.dp))
    Text("How well did you remember it?", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        RecallGrade.entries.forEach { grade ->
            val colors = if (grade == RecallGrade.FORGOT) {
                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.outlinedButtonColors()
            }
            OutlinedButton(
                onClick = { onGrade(grade) },
                colors = colors,
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                modifier = Modifier.weight(1f),
            ) {
                Text(grade.label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Instruction(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PromptWord(word: String) {
    Text(
        text = word,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
    )
}

/** Everything saved about the word, shown once it has been answered. */
@Composable
private fun WordDetails(word: WordEntity, showWord: Boolean = true) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (showWord) {
                Text(word.word, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
            word.meanings.forEachIndexed { i, m ->
                if (i > 0) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                }
                m.partOfSpeech?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(m.definition, style = MaterialTheme.typography.bodyLarge)
                m.example?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "“$it”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            word.contextSentence?.let {
                Spacer(Modifier.height(12.dp))
                Text("Where you found it", style = MaterialTheme.typography.labelMedium)
                Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
            }
            word.personalNote?.let {
                Spacer(Modifier.height(12.dp))
                Text("Your note", style = MaterialTheme.typography.labelMedium)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private enum class OptionVisual { Idle, Correct, Wrong, RevealedCorrect, Dimmed }

private fun optionVisual(index: Int, correctIndex: Int, selected: Int?): OptionVisual = when {
    selected == null -> OptionVisual.Idle
    index == correctIndex && index == selected -> OptionVisual.Correct
    index == correctIndex -> OptionVisual.RevealedCorrect
    index == selected -> OptionVisual.Wrong
    else -> OptionVisual.Dimmed
}

@Composable
private fun OptionCard(
    text: String,
    isWord: Boolean,
    visual: OptionVisual,
    onClick: () -> Unit,
) {
    val (bg, fg, borderColor) = when (visual) {
        OptionVisual.Idle -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.outlineVariant,
        )
        OptionVisual.Dimmed -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.outlineVariant,
        )
        OptionVisual.Correct, OptionVisual.RevealedCorrect -> Triple(CorrectGreen, Color.White, CorrectGreenBorder)
        OptionVisual.Wrong -> Triple(WrongRed, Color.White, WrongRedBorder)
    }
    val enabled = visual == OptionVisual.Idle
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(16.dp),
    ) {
        Text(
            text = text,
            color = fg,
            style = if (isWord) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun DoneState(state: QuizUiState.Done, onBack: () -> Unit) {
    val (title, body) = when {
        state.answered == 0 -> "All caught up." to
            "No words are due right now. Add new words or come back later."
        state.remainingDue == 0 -> "Quiz complete" to
            "${state.correct} of ${state.answered} remembered. You're all caught up."
        else -> "Quiz complete" to
            "${state.correct} of ${state.answered} remembered. ${state.remainingDue} more due — start another round when you're ready."
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text("Back to home") }
    }
}

private val CorrectGreen = Color(0xFF2E7D32)
private val CorrectGreenBorder = Color(0xFF1B5E20)
private val WrongRed = Color(0xFFC62828)
private val WrongRedBorder = Color(0xFFB71C1C)
