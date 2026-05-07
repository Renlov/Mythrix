package com.pimenov.character.api

import kotlinx.coroutines.flow.Flow

interface CharacterRepository {
    suspend fun save(sheet: CharacterSheet): Long
    suspend fun byId(id: Long): CharacterSheet?
    fun observeLatest(): Flow<CharacterSheet?>
    suspend fun clear()
}
