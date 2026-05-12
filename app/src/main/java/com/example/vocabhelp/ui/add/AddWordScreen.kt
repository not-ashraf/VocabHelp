package com.example.vocabhelp.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vocabhelp.audio.PronunciationPlayer
import com.example.vocabhelp.data.repo.DefinitionOption
import com.example.vocabhelp.data.repo.SaveOutcome
import com.example.vocabhelp.ui.common.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWordScreen(onBack: () -> Unit) {
    val vm = appViewModel { AddWordViewModel(it.wordRepository, it.workScheduler) }
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val player = remember { PronunciationPlayer() }

    LaunchedEffect(Unit) { focus.requestFocus() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add a word") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = state.word,
                onValueChange = vm::setWord,
                label = { Text("Word") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { vm.lookupNow() }),
                trailingIcon = {
                    IconButton(
                        onClick = vm::lookupNow,
                        enabled = state.canLookup,
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Look up meanings")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )

            Spacer(Modifier.height(16.dp))

            LookupSection(
                state = state,
                onSelect = vm::selectOption,
                onRetry = vm::lookupNow,
                onPlayAudio = { url -> player.play(url) },
                onManualMeaningChange = vm::setManualMeaning,
            )

            Spacer(Modifier.height(24.dp))

            Text("Optional", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.contextSentence,
                onValueChange = vm::setContext,
                label = { Text("Sentence where you found it") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.personalNote,
                onValueChange = vm::setNote,
                label = { Text("Note / mnemonic") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    vm.save { outcome ->
                        when (outcome) {
                            is SaveOutcome.Resolved,
                            is SaveOutcome.Pending,
                            is SaveOutcome.Duplicate -> onBack()
                            SaveOutcome.Empty -> Unit
                        }
                    }
                },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(saveButtonLabel(state))
            }
        }
    }
}

@Composable
private fun LookupSection(
    state: AddWordUiState,
    onSelect: (Int) -> Unit,
    onRetry: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onManualMeaningChange: (String) -> Unit,
) {
    when (val lookup = state.lookup) {
        LookupState.Idle -> {
            Text(
                "Type a word and tap the search icon to fetch its meanings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LookupState.Loading -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp).width(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Looking up…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        is LookupState.Found -> FoundSection(
            options = lookup.options,
            audioUrl = lookup.audioUrl,
            selectedIndex = state.selectedIndex,
            onSelect = onSelect,
            onPlayAudio = onPlayAudio,
        )
        LookupState.NotFound -> NotFoundOrErrorSection(
            title = "Not in the dictionary",
            subtitle = "Type the meaning yourself, or save it now and fix it later.",
            manual = state.manualMeaning,
            onManualChange = onManualMeaningChange,
            onRetry = onRetry,
            showRetry = false,
        )
        LookupState.NetworkError -> NotFoundOrErrorSection(
            title = "Couldn't reach the dictionary",
            subtitle = "Check your connection and retry, or save now and we'll fetch later.",
            manual = state.manualMeaning,
            onManualChange = onManualMeaningChange,
            onRetry = onRetry,
            showRetry = true,
        )
        is LookupState.AlreadySaved -> Text(
            "You've already saved this word.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun FoundSection(
    options: List<DefinitionOption>,
    audioUrl: String?,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    onPlayAudio: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Pick a meaning",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        audioUrl?.takeIf { it.isNotBlank() }?.let { url ->
            IconButton(onClick = { onPlayAudio(url) }) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Pronounce")
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    options.forEachIndexed { index, option ->
        DefinitionCard(
            option = option,
            selected = selectedIndex == index,
            onClick = { onSelect(index) },
        )
        if (index != options.lastIndex) Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DefinitionCard(
    option: DefinitionOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            option.partOfSpeech?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = option.definition,
                style = MaterialTheme.typography.bodyMedium,
            )
            option.example?.takeIf { it.isNotBlank() }?.let { ex ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "“$ex”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotFoundOrErrorSection(
    title: String,
    subtitle: String,
    manual: String,
    onManualChange: (String) -> Unit,
    onRetry: () -> Unit,
    showRetry: Boolean,
) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (showRetry) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Retry")
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = manual,
        onValueChange = onManualChange,
        label = { Text("Type meaning (optional)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
}

private fun saveButtonLabel(state: AddWordUiState): String = when {
    state.saving -> "Saving…"
    state.lookup is LookupState.Found && state.selectedIndex == null -> "Pick a meaning"
    state.lookup is LookupState.NotFound && state.manualMeaning.isBlank() -> "Save without meaning"
    state.lookup is LookupState.NetworkError && state.manualMeaning.isBlank() -> "Save without meaning"
    else -> "Save"
}
