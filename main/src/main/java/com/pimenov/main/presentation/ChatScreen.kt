package com.pimenov.main.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pimenov.feature.api.LlmMessage
import com.pimenov.main.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChatScreen(vm: ChatViewModel = koinViewModel()) {
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
                    icon = R.drawable.ic_avatar,
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
                EndingBar(state.outcome!!)
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
private fun EndingBar(outcome: String) {
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterSheet(
    player: PlayerSheet,
    onEquipToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Photo (left) + name and class (right).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_avatar),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = player.className,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Здоровье · ${player.hp}/${player.maxHp}",
                    style = MaterialTheme.typography.labelLarge,
                )
                LinearProgressIndicator(
                    progress = {
                        if (player.maxHp > 0) player.hp.toFloat() / player.maxHp else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            }

            Text(
                text = "Золото · ${player.gold}",
                style = MaterialTheme.typography.bodyLarge,
            )

            InventoryTabs(inventory = player.inventory, onToggle = onEquipToggle)
        }
    }
}

/** Item types that have their own sheet section; everything else goes to «Сумка». */
private val CATEGORY_TYPES = setOf("weapon", "armor", "ring")

private data class InventoryCategory(
    val title: String,
    val equippable: Boolean,
    val matches: (InventoryItem) -> Boolean,
)

private val INVENTORY_CATEGORIES = listOf(
    InventoryCategory("Оружие", equippable = true) { it.type == "weapon" },
    InventoryCategory("Броня", equippable = true) { it.type == "armor" },
    InventoryCategory("Кольца", equippable = true) { it.type == "ring" },
    InventoryCategory("Сумка", equippable = false) { it.type !in CATEGORY_TYPES },
)

/** Horizontal tabbed pager: tap a category tab or swipe to see its items. */
@Composable
private fun InventoryTabs(inventory: List<InventoryItem>, onToggle: (String) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { INVENTORY_CATEGORIES.size })
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            INVENTORY_CATEGORIES.forEachIndexed { index, category ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(category.title, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) { page ->
            val category = INVENTORY_CATEGORIES[page]
            InventoryPage(
                items = inventory.filter(category.matches),
                equippable = category.equippable,
                onToggle = onToggle,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InventoryPage(
    items: List<InventoryItem>,
    equippable: Boolean,
    onToggle: (String) -> Unit,
) {
    if (items.isEmpty()) {
        Text(
            text = "Пусто",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { ItemCard(it, equippable, onToggle) }
    }
}

@Composable
private fun ItemCard(item: InventoryItem, equippable: Boolean, onToggle: (String) -> Unit) {
    val borderColor = if (item.equipped) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        modifier = Modifier
            .width(76.dp)
            .then(if (equippable) Modifier.clickable { onToggle(item.id) } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, borderColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconForItemType(item.type)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
            if (item.equipped) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        itemStat(item)?.let { stat ->
            Text(
                text = stat,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun itemStat(item: InventoryItem): String? = when {
    item.damageDie != null -> "Урон ${item.damageDie}"
    item.armorBonus != null -> "Броня +${item.armorBonus}"
    else -> null
}

private fun iconForItemType(type: String): Int = when (type) {
    "weapon" -> R.drawable.ic_item_weapon
    "armor" -> R.drawable.ic_item_armor
    "ring" -> R.drawable.ic_item_ring
    "consumable" -> R.drawable.ic_item_consumable
    else -> R.drawable.ic_item_generic
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopSheet(
    wares: List<Ware>,
    ownedIds: Set<String>,
    gold: Int,
    onBuy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Товары трактирщика",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Золото · $gold",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            wares.forEach { ware ->
                WareRow(
                    ware = ware,
                    owned = ware.id in ownedIds,
                    canAfford = gold >= ware.price,
                    onBuy = { onBuy(ware.id) },
                )
            }
        }
    }
}

@Composable
private fun WareRow(ware: Ware, owned: Boolean, canAfford: Boolean, onBuy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconForItemType(ware.type)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = ware.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${ware.price} зол.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            owned -> Text(
                text = "Куплено",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Button(onClick = onBuy, enabled = canAfford) {
                Text(if (canAfford) "Купить" else "Мало золота")
            }
        }
    }
}

/**
 * Icon left of the input that opens a menu with two sections: the DM's
 * suggested actions (clickable) and read-only «Зацепки» the player uncovered.
 */
@Composable
private fun ActionPicker(
    options: List<String>,
    leads: List<String>,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = options.isNotEmpty() || leads.isNotEmpty()
    Box {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (active) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .clickable(enabled = active) { expanded = true }
                .padding(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_actions),
                contentDescription = "Действия и зацепки",
                tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (options.isNotEmpty()) {
                MenuSectionHeader("Действия")
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        enabled = enabled,
                        onClick = {
                            expanded = false
                            onPick(opt)
                        },
                    )
                }
            }
            if (leads.isNotEmpty()) {
                MenuSectionHeader("Зацепки")
                leads.forEach { lead ->
                    Text(
                        text = "• $lead",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
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
