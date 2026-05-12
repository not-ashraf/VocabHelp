package com.example.vocabhelp.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun statusToString(status: MeaningStatus): String = status.name

    @TypeConverter
    fun stringToStatus(value: String): MeaningStatus = MeaningStatus.valueOf(value)

    @TypeConverter
    fun meaningsToJson(meanings: List<MeaningEntry>): String =
        JSON.encodeToString(meanings)

    @TypeConverter
    fun jsonToMeanings(value: String): List<MeaningEntry> =
        if (value.isBlank()) emptyList() else JSON.decodeFromString(value)
}

private val JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}
