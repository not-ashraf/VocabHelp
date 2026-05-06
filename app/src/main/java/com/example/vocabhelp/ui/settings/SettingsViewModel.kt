package com.example.vocabhelp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vocabhelp.data.prefs.ReminderSettings
import com.example.vocabhelp.data.prefs.SettingsRepository
import com.example.vocabhelp.work.WorkScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val scheduler: WorkScheduler,
) : ViewModel() {

    val state: StateFlow<ReminderSettings> = settings.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReminderSettings(enabled = true, hour = 21, minute = 0),
        )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setEnabled(enabled)
            applySchedule()
        }
    }

    fun setTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settings.setTime(hour, minute)
            applySchedule()
        }
    }

    private suspend fun applySchedule() {
        val current = settings.settings.first()
        if (current.enabled) {
            scheduler.scheduleDailyReminder(current.hour, current.minute)
        } else {
            scheduler.cancelDailyReminder()
        }
    }
}
