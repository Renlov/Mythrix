package com.pimenov.game.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pimenov.character.api.CharacterRepository
import com.pimenov.character.api.CharacterSheet
import com.pimenov.game.api.ChatMessage
import com.pimenov.game.api.Enemy
import com.pimenov.game.api.GameRepository
import com.pimenov.game.api.GameSave
import com.pimenov.game.api.MessageAuthor
import com.pimenov.game.domain.ResolveAttackUseCase
import com.pimenov.game.domain.RollDiceUseCase
import com.pimenov.game.domain.SendPlayerMessageUseCase
import com.pimenov.game.domain.StartCombatUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameUiState(
    val character: CharacterSheet? = null,
    val save: GameSave? = null,
    val messages: List<ChatMessage> = emptyList(),
    val streamingDmText: String = "",
    val isSending: Boolean = false,
    val input: String = ""
)

class GameViewModel(
    private val gameRepo: GameRepository,
    private val characterRepo: CharacterRepository,
    private val sendMessage: SendPlayerMessageUseCase,
    private val rollDice: RollDiceUseCase,
    private val startCombat: StartCombatUseCase,
    private val resolveAttack: ResolveAttackUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                characterRepo.observeLatest(),
                gameRepo.observeMessages(),
                gameRepo.observeState()
            ) { character, messages, save -> Triple(character, messages, save) }
                .collect { (character, messages, save) ->
                    _state.update {
                        it.copy(character = character, messages = messages, save = save)
                    }
                    if (messages.isEmpty() && character != null) {
                        sendIntro()
                    }
                }
        }
    }

    private fun sendIntro() {
        viewModelScope.launch {
            gameRepo.appendMessage(
                ChatMessage(
                    author = MessageAuthor.SYSTEM,
                    content = "Приключение начинается."
                )
            )
            streamFromDm("Начни приключение")
        }
    }

    fun onInput(value: String) = _state.update { it.copy(input = value) }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.isSending) return
        _state.update { it.copy(input = "", isSending = true, streamingDmText = "") }
        viewModelScope.launch {
            streamFromDm(text)
            _state.update { it.copy(isSending = false, streamingDmText = "") }
        }
    }

    private suspend fun streamFromDm(text: String) {
        val sb = StringBuilder()
        sendMessage.invoke(text, _state.value.messages).collect { chunk ->
            sb.append(chunk)
            _state.update { it.copy(streamingDmText = sb.toString()) }
        }
    }

    fun rollD20() = appendSystem("Бросок d20: ${rollDice(20)}")
    fun rollD6() = appendSystem("Бросок d6: ${rollDice(6)}")
    fun rollD8() = appendSystem("Бросок d8: ${rollDice(8)}")

    private fun appendSystem(text: String) {
        viewModelScope.launch {
            gameRepo.appendMessage(ChatMessage(author = MessageAuthor.SYSTEM, content = text))
        }
    }

    fun beginSampleCombat() {
        val save = _state.value.save ?: GameSave(
            characterId = _state.value.character?.id ?: 0L,
            sceneTag = "dungeon"
        )
        viewModelScope.launch {
            val enemy = Enemy(
                name = "Гоблин",
                maxHp = 8,
                currentHp = 8,
                ac = 12,
                attackBonus = 3,
                damageDie = 6
            )
            startCombat(save, enemy)
        }
    }

    fun attack() {
        val save = _state.value.save ?: return
        val player = _state.value.character ?: return
        if (save.combat == null) return
        viewModelScope.launch {
            val result = resolveAttack(save, player)
            val verdict = when {
                result.ended && result.playerWon -> "Победа! Враг повержен."
                result.ended && !result.playerWon -> "Поражение… ваш герой пал."
                else -> "Бой продолжается. HP игрока: ${result.playerHp}, HP врага: ${result.newCombat.enemy.currentHp}"
            }
            gameRepo.appendMessage(
                ChatMessage(
                    author = MessageAuthor.SYSTEM,
                    content = result.newCombat.log.takeLast(2).joinToString("\n") + "\n" + verdict
                )
            )
            if (result.ended) {
                gameRepo.saveState(save.copy(combat = null))
            }
        }
    }

    fun dodge() = appendSystem("Вы готовитесь к защите. AC до конца хода +2.")
    fun cast() = appendSystem("Вы творите заклинание. Брось d8 для урона.")
}
