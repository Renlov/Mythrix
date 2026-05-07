package com.pimenov.game.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameSaveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GameSaveEntity)

    @Query("SELECT * FROM game_save WHERE id = 1")
    suspend fun current(): GameSaveEntity?

    @Query("SELECT * FROM game_save WHERE id = 1")
    fun observe(): Flow<GameSaveEntity?>

    @Query("DELETE FROM game_save")
    suspend fun clear()
}

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}
