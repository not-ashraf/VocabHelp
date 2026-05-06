package com.example.vocabhelp.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vocabhelp.ui.common.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(onBack: () -> Unit) {
    val vm = appViewModel { QuizViewModel(it.wordRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quiz") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val s = state) {
                QuizUiState.Loading -> CircularProgressIndicator()
                QuizUiState.Done -> DoneState(onBack = onBack)
                is QuizUiState.Question -> QuestionContent(
                    state = s,
                    onAnswer = vm::answer,
                    onNext = vm::loadNext,
                )
            }
        }
    }
}

@Composable
private fun QuestionContent(
    state: QuizUiState.Question,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = state.target.word,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        )
        Text(
            text = "Pick the correct meaning:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        state.options.forEachIndexed { index, option ->
            OptionCard(
                text = option.text,
                state = optionState(index, state),
                onClick = { onAnswer(index) },
            )
            Spacer(Modifier.height(12.dp))
        }
        if (state.selectedIndex != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text("Next")
            }
        }
    }
}

private enum class OptionVisual { Idle, Selected, Correct, Wrong, RevealedCorrect }

private fun optionState(index: Int, q: QuizUiState.Question): OptionVisual {
    val selected = q.selectedIndex
    return when {
        selected == null -> OptionVisual.Idle
        index == q.correctIndex && index == selected -> OptionVisual.Correct
        index == q.correctIndex && index != selected -> OptionVisual.RevealedCorrect
        index == selected -> OptionVisual.Wrong
        else -> OptionVisual.Idle
    }
}

@Composable
private fun OptionCard(
    text: String,
    state: OptionVisual,
    onClick: () -> Unit,
) {
    val (bg, fg, borderColor) = when (state) {
        OptionVisual.Idle -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.outlineVariant,
        )
        OptionVisual.Selected -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primary,
        )
        OptionVisual.Correct, OptionVisual.RevealedCorrect -> Triple(
            CorrectGreen,
            Color.White,
            CorrectGreenBorder,
        )
        OptionVisual.Wrong -> Triple(
            WrongRed,
            Color.White,
            WrongRedBorder,
        )
    }
    val enabled = state == OptionVisual.Idle
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(16.dp),
    ) {
        Text(text = text, color = fg, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DoneState(onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(
            "All caught up.",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No words are due right now. Add new words or come back later.",
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
