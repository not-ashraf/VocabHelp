package com.example.vocabhelp.ui.nav

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data class AddWord(val word: String = "") : Route
    @Serializable data class WordDetail(val id: Long) : Route
    @Serializable data object Quiz : Route
    @Serializable data object Settings : Route
}
