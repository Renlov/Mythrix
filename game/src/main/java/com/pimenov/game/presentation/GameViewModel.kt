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
import com.pimenov.game.api.Plot
import com.pimenov.game.domain.AdvancePlotUseCase
import com.pimenov.game.domain.RecruitCompanionUseCase
import com.pimenov.game.domain.ResolveAttackUseCase
import com.pimenov.game.domain.RollDiceUseCase
import com.pimenov.game.domain.SendPlayerMessageUseCase
import com.pimenov.game.domain.StartCombatUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class GameUiState(
    val character: CharacterSheet? = null,
    val save: GameSave? = null,
    val messages: List<ChatMessage> = emptyList(),
    val streamingDmText: String = "",
    val isSending: Boolean = false,
    val input: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModel(
    private val gameRepo: GameRepository,
    private val characterRepo: CharacterRepository,
    private val sendMessage: SendPlayerMessageUseCase,
    private val rollDice: RollDiceUseCase,
    private val startCombat: StartCombatUseCase,
    private val resolveAttack: ResolveAttackUseCase,
    private val advancePlot: AdvancePlotUseCase,
    private val recruit: RecruitCompanionUseCase
) : ViewModel() {

    val currentStage: Plot.Stage?
        get() = _state.value.save?.let { Plot.stageAt(it.stageIndex) }

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    @Volatile private var introScheduled: Boolean = false
    init {
        // Load the character bound to the current save.
        // Falls back to "latest created" only if the save has no characterId yet.
        viewModelScope.launch {
            gameRepo.observeState()
                .map { it?.characterId }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    when {
                        id == null -> characterRepo.observeLatest()
                        id == 0L -> characterRepo.observeLatest()
                        else -> flowOf(characterRepo.byId(id))
                    }
                }
                .collect { ch ->
                    _state.update { it.copy(character = ch) }
                    maybeIntro()
                }
        }
        viewModelScope.launch {
            gameRepo.observeMessages().collect { messages ->
                _state.update { it.copy(messages = messages) }
maybeIntro()
            }
        }
        viewModelScope.launch {
            gameRepo.observeState().collect { save ->
                _state.update { it.copy(save = save) }
            }
        }
    }

    private fun maybeIntro() {
        if (introScheduled) return
        val s = _state.value
        if (s.character != null && s.messages.isEmpty()) {
            introScheduled = true
            sendIntro()
        }
    }

    private fun sendIntro() {
        viewModelScope.launch {
            val save = gameRepo.loadState()
            val stage = save?.let { Plot.stageAt(it.stageIndex) }
            if (stage != null) {
                gameRepo.appendMessage(
                    ChatMessage(
                        author = MessageAuthor.SYSTEM,
                        content = "Этап ${stage.index + 1}/${Plot.DRAGON_TOWER.size}: ${stage.title}"
                    )
                )
                // DM speaks first — guaranteed, regardless of LLM availability.
                gameRepo.appendMessage(
                    ChatMessage(
                        author = MessageAuthor.DM,
                        content = buildIntroNarration(stage)
                    )
                )
            } else {
                gameRepo.appendMessage(
                    ChatMessage(
                        author = MessageAuthor.SYSTEM,
                        content = "Приключение начинается."
                    )
                )
            }
        }
    }

    private fun buildIntroNarration(stage: Plot.Stage): String {
        val sb = StringBuilder()
        sb.append(stage.situation)
        stage.encounter?.let { enc ->
            sb.append("\n\n")
            sb.append(enc.description)
            when (enc.stance) {
                Plot.Stance.RECRUITABLE ->
                    sb.append(" Похоже, ${enc.name.split(' ').first()} может пойти с тобой — стоит попробовать.")
                Plot.Stance.HOSTILE ->
                    sb.append(" Это враг. Готовься к бою.")
                Plot.Stance.NEUTRAL -> Unit
            }
        }
        return sb.toString()
    }

    fun onInput(value: String) = _state.update { it.copy(input = value) }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.isSending) return
        dispatch(text, clearInput = true)
    }

    fun sendQuick(text: String) {
        if (text.isBlank() || _state.value.isSending) return
        dispatch(text, clearInput = false)
    }

    private fun dispatch(text: String, clearInput: Boolean) {
        _state.update {
            it.copy(
                input = if (clearInput) "" else it.input,
                isSending = true,
                streamingDmText = ""
            )
        }
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

    /** Start combat with the current stage's hostile NPC, if any. */
    fun engageStageEnemy() {
        val save = _state.value.save ?: return
        val stage = Plot.stageAt(save.stageIndex)
        val enemy = stage.encounter?.enemy ?: return
        viewModelScope.launch { startCombat(save, enemy) }
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
                val stage = Plot.stageAt(save.stageIndex)
                val princessSaved = save.princessSaved ||
                    (stage.isFinale && result.playerWon)
                gameRepo.saveState(save.copy(combat = null, princessSaved = princessSaved))
                if (princessSaved && !save.princessSaved) {
                    gameRepo.appendMessage(
                        ChatMessage(
                            author = MessageAuthor.SYSTEM,
                            content = "Принцесса Алинара спасена! Драгоценная победа."
                        )
                    )
                }
                if (result.playerWon && !princessSaved) advancePlot()
            }
        }
    }

    fun dodge() = appendSystem("Вы готовитесь к защите. AC до конца хода +2.")
    fun cast() = appendSystem("Вы творите заклинание. Брось d8 для урона.")

    fun advanceStage() {
        viewModelScope.launch { advancePlot() }
    }

    fun recruitCurrent() {
        viewModelScope.launch {
            val ok = recruit()
            if (!ok) {
                gameRepo.appendMessage(
                    ChatMessage(
                        author = MessageAuthor.SYSTEM,
                        content = "Сейчас никого нельзя завербовать."
                    )
                )
            } else {
                advancePlot()
            }
        }
    }
}
