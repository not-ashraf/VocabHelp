package com.example.vocabhelp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [WordEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VocabDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao

    companion object {
        @Volatile private var INSTANCE: VocabDatabase? = null

        fun get(context: Context): VocabDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VocabDatabase::class.java,
                    "vocab.db",
                )
                    // The device holds real saved words: never fall back to a destructive
                    // migration. Any schema change must bump `version` and add a Migration
                    // (compare against the exported JSON in app/schemas/).
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
