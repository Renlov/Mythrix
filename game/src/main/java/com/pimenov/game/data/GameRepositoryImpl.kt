package com.pimenov.game.data

import com.pimenov.game.api.ChatMessage
import com.pimenov.game.api.CombatState
import com.pimenov.game.api.GameRepository
import com.pimenov.game.api.GameSave
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class GameRepositoryImpl(
    private val saveDao: GameSaveDao,
    private val chatDao: ChatMessageDao,
    private val json: Json
) : GameRepository {

    override suspend fun appendMessage(message: ChatMessage): Long =
        chatDao.insert(ChatMessageEntity.fromDomain(message))

    override fun observeMessages(): Flow<List<ChatMessage>> =
        chatDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun saveState(save: GameSave) {
        saveDao.upsert(
            GameSaveEntity(
                id = 1L,
                characterId = save.characterId,
                sceneTag = save.sceneTag,
                combatJson = save.combat?.let { json.encodeToString(CombatState.serializer(), it) },
                stageIndex = save.stageIndex,
                companionsJson = if (save.companions.isEmpty()) null
                else json.encodeToString(ListSerializer(String.serializer()), save.companions),
                princessSaved = save.princessSaved
            )
        )
    }

    override suspend fun loadState(): GameSave? = saveDao.current()?.toDomain(json)

    override fun observeState(): Flow<GameSave?> =
        saveDao.observe().map { it?.toDomain(json) }

    override suspend fun clearAll() {
        chatDao.clear()
        saveDao.clear()
    }
}

internal fun GameSaveEntity.toDomain(json: Json): GameSave = GameSave(
    characterId = characterId,
    sceneTag = sceneTag,
    combat = combatJson?.let { json.decodeFromString(CombatState.serializer(), it) },
    stageIndex = stageIndex,
    companions = companionsJson?.let {
        json.decodeFromString(ListSerializer(String.serializer()), it)
    } ?: emptyList(),
    princessSaved = princessSaved
)
