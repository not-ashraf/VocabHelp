package com.example.vocabhelp.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vocabhelp.data.db.WordEntity
import com.example.vocabhelp.data.repo.WordRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WordDetailViewModel(
    private val repo: WordRepository,
    private val id: Long,
) : ViewModel() {

    val word: StateFlow<WordEntity?> = repo.observeById(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveManualMeaning(meaning: String) {
        viewModelScope.launch { repo.setManualMeaning(id, meaning) }
    }

    fun retryLookup() {
        viewModelScope.launch { repo.resolveLookup(id) }
    }

    fun saveUserFields(context: String, note: String) {
        viewModelScope.launch { repo.updateUserFields(id, context, note) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.getById(id)?.let { repo.delete(it) }
            onDone()
        }
    }
}
