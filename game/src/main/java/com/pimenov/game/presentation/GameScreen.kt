package com.pimenov.game.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pimenov.game.api.ChatMessage
import com.pimenov.game.api.MessageAuthor
import com.pimenov.uikit.components.BubbleAuthor
import com.pimenov.uikit.components.ChatBubble
import com.pimenov.uikit.components.DiceButton
import com.pimenov.uikit.components.FantasyBackground
import com.pimenov.uikit.components.GlassCard
import com.pimenov.uikit.components.PrimaryActionButton
import com.pimenov.uikit.components.sceneTagFrom
import org.koin.androidx.compose.koinViewModel

@Composable
fun GameScreen(viewModel: GameViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sceneTag = sceneTagFrom(state.save?.sceneTag)
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.streamingDmText) {
        val target = state.messages.size + if (state.streamingDmText.isNotEmpty()) 1 else 0
        if (target > 0) listState.animateScrollToItem(target - 1)
    }

    FantasyBackground(tag = sceneTag) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(8.dp)
        ) {
            CombatHud(state)
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(state.messages, key = { it.id }) { msg -> Message(msg) }
                    val last = state.messages.lastOrNull()
                    val showStreaming = state.streamingDmText.isNotEmpty() &&
                        !(last?.author == MessageAuthor.DM && last.content == state.streamingDmText)
                    if (showStreaming) {
                        item {
                            ChatBubble(text = state.streamingDmText, author = BubbleAuthor.DM)
                        }
                    }
                }
            }
            if (state.save?.combat != null) {
                DiceTray(viewModel)
                CombatActions(viewModel)
            } else {
                QuickActions(onSend = viewModel::sendQuick, enabled = !state.isSending)
            }
            InputRow(state.input, viewModel::onInput, viewModel::send, state.isSending)
        }
    }
}

@Composable
private fun Message(message: ChatMessage) {
    val author = when (message.author) {
        MessageAuthor.PLAYER -> BubbleAuthor.PLAYER
        MessageAuthor.DM -> BubbleAuthor.DM
        MessageAuthor.SYSTEM -> BubbleAuthor.SYSTEM
    }
    ChatBubble(text = message.content, author = author)
}

@Composable
private fun CombatHud(state: GameUiState) {
    val player = state.character ?: return
    val combat = state.save?.combat
    GlassCard(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        Column {
            Text(
                "${player.name} • HP ${player.currentHp}/${player.maxHp} • AC ${player.ac}",
                color = MaterialTheme.colorScheme.onSurface
            )
            if (combat != null) {
                Text(
                    "Враг: ${combat.enemy.name} • HP ${combat.enemy.currentHp}/${combat.enemy.maxHp} • AC ${combat.enemy.ac}",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun DiceTray(vm: GameViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        DiceButton(label = "d20", onClick = vm::rollD20)
        DiceButton(label = "d6", onClick = vm::rollD6)
        DiceButton(label = "d8", onClick = vm::rollD8)
        PrimaryActionButton(label = "Старт боя", onClick = vm::beginSampleCombat)
    }
}

@Composable
private fun CombatActions(vm: GameViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PrimaryActionButton(label = strRes("game_attack"), onClick = vm::attack, modifier = Modifier.weight(1f))
        PrimaryActionButton(label = strRes("game_dodge"), onClick = vm::dodge, modifier = Modifier.weight(1f))
        PrimaryActionButton(label = strRes("game_cast"), onClick = vm::cast, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuickActions(onSend: (String) -> Unit, enabled: Boolean) {
    val actions = listOf(
        strRes("quick_look") to strRes("quick_look_text"),
        strRes("quick_talk") to strRes("quick_talk_text"),
        strRes("quick_search") to strRes("quick_search_text"),
        strRes("quick_rest") to strRes("quick_rest_text"),
        strRes("quick_sneak") to strRes("quick_sneak_text")
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(actions) { (label, text) ->
            AssistChip(
                onClick = { if (enabled) onSend(text) },
                label = { Text(label) },
                enabled = enabled
            )
        }
    }
}

@Composable
private fun InputRow(value: String, onChange: (String) -> Unit, onSend: () -> Unit, sending: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(strRes("game_input_hint")) },
            modifier = Modifier.weight(1f).height(56.dp),
            enabled = !sending,
            singleLine = true
        )
        PrimaryActionButton(label = strRes("game_send"), onClick = onSend, enabled = !sending)
    }
}

@Composable
private fun strRes(name: String): String {
    val ctx = LocalContext.current
    val id = ctx.resources.getIdentifier(name, "string", ctx.packageName)
    return if (id != 0) ctx.getString(id) else name
}
