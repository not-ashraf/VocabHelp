package com.example.vocabhelp.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vocab_settings")

class SettingsRepository(context: Context) {
    private val ds = context.applicationContext.dataStore

    val settings: Flow<ReminderSettings> = ds.data.map { prefs ->
        ReminderSettings(
            enabled = prefs[KEY_ENABLED] ?: true,
            hour = prefs[KEY_HOUR] ?: DEFAULT_HOUR,
            minute = prefs[KEY_MINUTE] ?: DEFAULT_MINUTE,
        )
    }

    val notificationPromptDismissed: Flow<Boolean> = ds.data.map { prefs ->
        prefs[KEY_NOTIF_PROMPT_DISMISSED] ?: false
    }

    suspend fun setEnabled(enabled: Boolean) {
        ds.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setTime(hour: Int, minute: Int) {
        ds.edit {
            it[KEY_HOUR] = hour.coerceIn(0, 23)
            it[KEY_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setNotificationPromptDismissed(dismissed: Boolean) {
        ds.edit { it[KEY_NOTIF_PROMPT_DISMISSED] = dismissed }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_HOUR = intPreferencesKey("reminder_hour")
        val KEY_MINUTE = intPreferencesKey("reminder_minute")
        val KEY_NOTIF_PROMPT_DISMISSED = booleanPreferencesKey("notif_prompt_dismissed")
        const val DEFAULT_HOUR = 21
        const val DEFAULT_MINUTE = 0
    }
}

data class ReminderSettings(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
)
