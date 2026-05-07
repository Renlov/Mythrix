package com.pimenov.character.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CharacterEntity): Long

    @Query("SELECT * FROM characters ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<CharacterEntity?>

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun byId(id: Long): CharacterEntity?

    @Query("DELETE FROM characters")
    suspend fun clear()
}
