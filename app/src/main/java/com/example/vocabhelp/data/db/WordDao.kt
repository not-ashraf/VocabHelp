package com.example.vocabhelp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {

    @Query(
        """
        SELECT * FROM words
        WHERE :query = ''
           OR word LIKE '%' || :query || '%'
           OR IFNULL(meaning, '') LIKE '%' || :query || '%'
        ORDER BY nextReviewAt ASC, createdAt DESC
        """,
    )
    fun observeAll(query: String): Flow<List<WordEntity>>

    @Query(
        """
        SELECT * FROM words
        WHERE meaning IS NOT NULL
          AND nextReviewAt <= :now
        ORDER BY nextReviewAt ASC
        """,
    )
    fun observeDue(now: Long): Flow<List<WordEntity>>

    @Query(
        """
        SELECT * FROM words
        WHERE meaning IS NOT NULL
          AND nextReviewAt <= :now
        ORDER BY nextReviewAt ASC
        """,
    )
    suspend fun getDueOnce(now: Long): List<WordEntity>

    @Query(
        """
        SELECT COUNT(*) FROM words
        WHERE meaning IS NOT NULL
          AND nextReviewAt <= :now
        """,
    )
    fun countDue(now: Long): Flow<Int>

    @Query(
        """
        SELECT * FROM words
        WHERE id != :excludingId
          AND meaning IS NOT NULL
        ORDER BY RANDOM()
        LIMIT :limit
        """,
    )
    suspend fun getRandomDistractors(excludingId: Long, limit: Int = 3): List<WordEntity>

    @Query("SELECT * FROM words WHERE status = 'PENDING_LOOKUP'")
    fun observePending(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE status = 'PENDING_LOOKUP'")
    suspend fun getPendingOnce(): List<WordEntity>

    @Query("SELECT * FROM words WHERE id = :id")
    fun observeById(id: Long): Flow<WordEntity?>

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getById(id: Long): WordEntity?

    @Query("SELECT * FROM words WHERE word = :word LIMIT 1")
    suspend fun getByWord(word: String): WordEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: WordEntity): Long

    @Update
    suspend fun update(word: WordEntity)

    @Upsert
    suspend fun upsert(word: WordEntity): Long

    @Delete
    suspend fun delete(word: WordEntity)
}
