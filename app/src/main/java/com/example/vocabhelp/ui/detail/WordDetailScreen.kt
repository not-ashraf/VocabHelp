package com.example.vocabhelp.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vocabhelp.audio.PronunciationPlayer
import com.example.vocabhelp.data.db.MeaningStatus
import com.example.vocabhelp.data.db.WordEntity
import com.example.vocabhelp.ui.common.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailScreen(
    id: Long,
    onBack: () -> Unit,
) {
    val vm = appViewModel(key = "detail-$id") { WordDetailViewModel(it.wordRepository, id) }
    val word by vm.word.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val player = remember { PronunciationPlayer(context) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(word?.word ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { padding ->
        val w = word
        if (w == null) {
            Spacer(Modifier.fillMaxSize())
            return@Scaffold
        }
        DetailContent(
            word = w,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            onPlayAudio = { player.play(w.word, w.phoneticAudioUrl) },
            onRetryLookup = vm::retryLookup,
            onSaveManual = vm::saveManualMeaning,
            onSaveUserFields = vm::saveUserFields,
        )
    }

    if (showDeleteDialog) {
        DeleteWordDialog(
            word = word?.word.orEmpty(),
            onConfirm = {
                showDeleteDialog = false
                vm.delete(onBack)
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun DeleteWordDialog(word: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        title = { Text("Delete \"$word\"?") },
        text = { Text("This will remove the word and its review history. This can't be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DetailContent(
    word: WordEntity,
    modifier: Modifier = Modifier,
    onPlayAudio: () -> Unit,
    onRetryLookup: () -> Unit,
    onSaveManual: (String) -> Unit,
    onSaveUserFields: (String, String) -> Unit,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = word.word,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onPlayAudio) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Pronounce")
            }
        }
        Spacer(Modifier.height(16.dp))
        when (word.status) {
            MeaningStatus.PENDING_LOOKUP -> PendingMeaningSection(
                onRetry = onRetryLookup,
                onSaveManual = onSaveManual,
            )
            MeaningStatus.RESOLVED, MeaningStatus.MANUAL -> ResolvedMeaningSection(word)
        }
        Spacer(Modifier.height(24.dp))
        OptionalUserFields(word, onSaveUserFields)
    }
}

@Composable
private fun ResolvedMeaningSection(word: WordEntity) {
    word.meanings.forEachIndexed { index, entry ->
        if (index > 0) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
        }
        entry.partOfSpeech?.takeIf { it.isNotBlank() }?.let { pos ->
            Text(
                text = pos,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = entry.definition,
            style = MaterialTheme.typography.bodyLarge,
        )
        entry.example?.takeIf { it.isNotBlank() }?.let { example ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = "“$example”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PendingMeaningSection(
    onRetry: () -> Unit,
    onSaveManual: (String) -> Unit,
) {
    var manual by rememberSaveable { mutableStateOf("") }
    Text(
        text = "Meaning not fetched yet.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = onRetry,
            label = { Text("Retry lookup") },
            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            colors = AssistChipDefaults.assistChipColors(),
        )
    }
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = manual,
        onValueChange = { manual = it },
        label = { Text("Or type the meaning manually") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { onSaveManual(manual) },
        enabled = manual.isNotBlank(),
    ) {
        Text("Save meaning")
    }
}

@Composable
private fun OptionalUserFields(
    word: WordEntity,
    onSave: (String, String) -> Unit,
) {
    var ctx by rememberSaveable(word.id) { mutableStateOf(word.contextSentence.orEmpty()) }
    var note by rememberSaveable(word.id) { mutableStateOf(word.personalNote.orEmpty()) }
    Text(
        text = "Optional",
        style = MaterialTheme.typography.labelMedium,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = ctx,
        onValueChange = { ctx = it },
        label = { Text("Sentence where you found it") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        label = { Text("Note / mnemonic") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = { onSave(ctx, note) }) {
        Text("Save")
    }
}
