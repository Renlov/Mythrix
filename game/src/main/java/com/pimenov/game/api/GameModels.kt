package com.pimenov.game.api

import kotlinx.serialization.Serializable

@Serializable
data class Enemy(
    val name: String,
    val maxHp: Int,
    val currentHp: Int,
    val ac: Int,
    val attackBonus: Int,
    val damageDie: Int
)

@Serializable
data class CombatState(
    val enemy: Enemy,
    val playerTurn: Boolean = true,
    val log: List<String> = emptyList()
)

@Serializable
data class GameSave(
    val characterId: Long,
    val sceneTag: String = "tavern",
    val combat: CombatState? = null,
    val stageIndex: Int = 0,
    val companions: List<String> = emptyList(),
    val princessSaved: Boolean = false
)

enum class MessageAuthor { PLAYER, DM, SYSTEM }

data class ChatMessage(
    val id: Long = 0L,
    val author: MessageAuthor,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
