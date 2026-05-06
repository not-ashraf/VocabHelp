package com.example.vocabhelp.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun statusToString(status: MeaningStatus): String = status.name

    @TypeConverter
    fun stringToStatus(value: String): MeaningStatus = MeaningStatus.valueOf(value)
}
