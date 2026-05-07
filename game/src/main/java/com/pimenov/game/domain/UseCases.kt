package com.pimenov.game.domain

import com.pimenov.character.api.CharacterSheet
import com.pimenov.core.common.Dice
import com.pimenov.feature.api.LlmEngine
import com.pimenov.feature.api.LlmMessage
import com.pimenov.game.api.ChatMessage
import com.pimenov.game.api.CombatState
import com.pimenov.game.api.Enemy
import com.pimenov.game.api.GameRepository
import com.pimenov.game.api.GameSave
import com.pimenov.game.api.MessageAuthor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SendPlayerMessageUseCase(
    private val repo: GameRepository,
    private val engine: LlmEngine
) {
    fun invoke(content: String, history: List<ChatMessage>): Flow<String> = flow {
        repo.appendMessage(ChatMessage(author = MessageAuthor.PLAYER, content = content))
        val mapped = history.map {
            LlmMessage(
                role = when (it.author) {
                    MessageAuthor.PLAYER -> LlmMessage.Role.USER
                    MessageAuthor.DM -> LlmMessage.Role.ASSISTANT
                    MessageAuthor.SYSTEM -> LlmMessage.Role.SYSTEM
                },
                content = it.content
            )
        }
        val sb = StringBuilder()
        engine.generate(content, mapped).collect { chunk ->
            sb.append(chunk)
            emit(chunk)
        }
        repo.appendMessage(ChatMessage(author = MessageAuthor.DM, content = sb.toString()))
    }
}

class RollDiceUseCase {
    operator fun invoke(sides: Int, modifier: Int = 0): Int = Dice.roll(sides, 1, modifier)
}

class StartCombatUseCase(private val repo: GameRepository) {
    suspend operator fun invoke(save: GameSave, enemy: Enemy): GameSave {
        val updated = save.copy(
            combat = CombatState(enemy = enemy, playerTurn = true, log = listOf("Бой начался: ${enemy.name}!"))
        )
        repo.saveState(updated)
        repo.appendMessage(ChatMessage(author = MessageAuthor.SYSTEM, content = "Начался бой: ${enemy.name}"))
        return updated
    }
}

data class AttackResult(
    val attackRoll: Int,
    val total: Int,
    val hit: Boolean,
    val damage: Int,
    val newCombat: CombatState,
    val playerHp: Int,
    val ended: Boolean,
    val playerWon: Boolean
)

class ResolveAttackUseCase(private val repo: GameRepository) {
    suspend operator fun invoke(
        save: GameSave,
        player: CharacterSheet
    ): AttackResult {
        val combat = save.combat ?: error("No combat in progress")
        val enemy = combat.enemy
        val roll = Dice.roll(20)
        val total = roll + player.attackBonus
        val hit = total >= enemy.ac
        val damage = if (hit) Dice.roll(player.damageDie, 1, player.stats.strMod).coerceAtLeast(1) else 0
        val enemyHp = (enemy.currentHp - damage).coerceAtLeast(0)
        val log = combat.log + buildString {
            append("Атака: d20=$roll +${player.attackBonus} = $total против AC ${enemy.ac} → ")
            append(if (hit) "попадание, урон $damage" else "промах")
        }

        var playerHp = player.currentHp
        var ended = enemyHp <= 0
        var playerWon = ended

        val updatedCombat = if (ended) {
            combat.copy(enemy = enemy.copy(currentHp = 0), log = log + "Враг повержен!")
        } else {
            val enemyRoll = Dice.roll(20)
            val enemyTotal = enemyRoll + enemy.attackBonus
            val enemyHit = enemyTotal >= 13
            val enemyDamage = if (enemyHit) Dice.roll(enemy.damageDie, 1, 1) else 0
            playerHp = (player.currentHp - enemyDamage).coerceAtLeast(0)
            val log2 = log + buildString {
                append("Враг атакует: d20=$enemyRoll +${enemy.attackBonus} = $enemyTotal → ")
                append(if (enemyHit) "попадание, урон $enemyDamage" else "промах")
            }
            ended = playerHp <= 0
            combat.copy(enemy = enemy.copy(currentHp = enemyHp), log = log2)
        }

        val updatedSave = save.copy(combat = if (ended) null else updatedCombat)
        repo.saveState(updatedSave)
        return AttackResult(
            attackRoll = roll,
            total = total,
            hit = hit,
            damage = damage,
            newCombat = updatedCombat,
            playerHp = playerHp,
            ended = ended,
            playerWon = playerWon || (ended && enemyHp <= 0)
        )
    }
}

class LoadSaveUseCase(private val repo: GameRepository) {
    suspend operator fun invoke(): GameSave? = repo.loadState()
}

class SaveGameUseCase(private val repo: GameRepository) {
    suspend operator fun invoke(save: GameSave) = repo.saveState(save)
}
