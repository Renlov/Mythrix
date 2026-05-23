package com.pimenov.main.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pimenov.feature.api.LlmMessage
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChatScreen(
    vm: ChatViewModel = koinViewModel(),
    onRestart: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var showSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val visible = state.visibleMessages

    if (showSheet) {
        CharacterSheet(
            player = state.player,
            onEquipToggle = { vm.toggleEquip(it) },
            onDismiss = { showSheet = false },
        )
    }
    if (state.showShop) {
        ShopSheet(
            wares = state.wares,
            ownedIds = state.ownedItemIds,
            gold = state.player.gold,
            onBuy = { vm.buy(it) },
            onDismiss = { vm.dismissShop() },
        )
    }

    LaunchedEffect(visible.size, visible.lastOrNull()?.text?.length) {
        if (visible.isNotEmpty()) {
            listState.animateScrollToItem(visible.lastIndex)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuestBanner(quest = state.quest, modifier = Modifier.weight(1f))
                TopIconButton(
                    icon = avatarDrawable(state.player.avatar),
                    contentDescription = "Персонаж",
                    onClick = { showSheet = true },
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(visible, key = { it.id }) { msg ->
                    MessageBubble(
                        msg = msg,
                        onShopClick = { vm.openShop() },
                    )
                }
            }

            state.error?.let { err ->
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }

            if (state.outcome != null) {
                EndingBar(state.outcome!!, onRestart = onRestart)
            } else Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionPicker(
                    options = state.currentOptions,
                    leads = state.leads,
                    enabled = !state.isSending,
                    onPick = { vm.send(it) },
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Что ты делаешь?") },
                    enabled = !state.isSending,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        val text = input
                        input = ""
                        vm.send(text)
                    },
                    enabled = !state.isSending && input.isNotBlank(),
                ) {
                    if (state.isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Ход")
                    }
                }
            }
        }
    }
}

/** Bottom-of-screen pilot ending, replacing the input once the game is over. */
@Composable
private fun EndingBar(outcome: String, onRestart: () -> Unit) {
    val victory = outcome == "victory"
    val container = if (victory) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.errorContainer
    val onContainer = if (victory) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onErrorContainer
    Surface(color = container, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (victory) "Победа" else "Гибель",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = onContainer,
            )
            Text(
                text = if (victory) "Дракон повержен. Пилот пройден."
                else "Дорога окончилась здесь.",
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer,
            )
            Button(onClick = onRestart) {
                Text("Новая игра")
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, onShopClick: () -> Unit) {
    val isUser = msg.role == LlmMessage.Role.USER
    val bg = if (isUser) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val widthFraction = if (isUser) 0.80f else 0.95f
    val linkColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = when {
                    msg.text.isEmpty() && msg.isStreaming -> AnnotatedString("…")
                    isUser -> withBoldQuotes(msg.text)
                    else -> dmText(msg.text, linkColor, onShopClick)
                },
                color = fg,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun QuestBanner(quest: QuestObjective, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Цель · ${quest.title}",
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            quest.steps.forEach { step ->
                Text(
                    text = "${if (step.done) "✓" else "○"} ${step.label}",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Top-bar pill icon button (same height and color across the bar). */
@Composable
private fun TopIconButton(icon: Int, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable { onClick() }
            .padding(8.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(24.dp),
        )
    }
}

private const val SHOP_WORD = "Магазин"

/**
 * DM text: quoted speech in bold, plus the word «Магазин» rendered as an
 * underlined link that opens the shop menu.
 */
private fun dmText(text: String, linkColor: Color, onShop: () -> Unit): AnnotatedString =
    buildAnnotatedString {
        var idx = 0
        while (true) {
            val found = text.indexOf(SHOP_WORD, idx)
            if (found < 0) {
                appendBoldQuotes(text.substring(idx))
                break
            }
            appendBoldQuotes(text.substring(idx, found))
            withLink(
                LinkAnnotation.Clickable(
                    tag = "SHOP",
                    styles = TextLinkStyles(
                        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    ),
                ) { onShop() },
            ) {
                append(SHOP_WORD)
            }
            idx = found + SHOP_WORD.length
        }
    }

private fun withBoldQuotes(text: String): AnnotatedString =
    buildAnnotatedString { appendBoldQuotes(text) }

/**
 * Appends [text] to the builder, rendering quoted speech (between `«…»` or
 * straight `"…"`) in bold. Unmatched quotes fall through as plain text.
 */
private fun AnnotatedString.Builder.appendBoldQuotes(text: String) {
    if (text.isEmpty()) return
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        val openIdx = when (ch) { '«' -> i; '"' -> i; else -> -1 }
        if (openIdx < 0) {
            append(ch)
            i++
            continue
        }
        val closeChar = if (ch == '«') '»' else '"'
        val closeIdx = text.indexOf(closeChar, openIdx + 1)
        if (closeIdx < 0) {
            append(text.substring(openIdx))
            return
        }
        withStyle(bold) {
            append(text.substring(openIdx, closeIdx + 1))
        }
        i = closeIdx + 1
    }
}
