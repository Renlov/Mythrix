package com.pimenov.game.api

import kotlinx.coroutines.flow.Flow

interface GameRepository {
    suspend fun appendMessage(message: ChatMessage): Long
    fun observeMessages(): Flow<List<ChatMessage>>
    suspend fun saveState(save: GameSave)
    suspend fun loadState(): GameSave?
    fun observeState(): Flow<GameSave?>
    suspend fun clearAll()
}
