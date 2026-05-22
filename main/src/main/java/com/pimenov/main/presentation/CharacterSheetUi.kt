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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pimenov.main.R
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterSheet(
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

/** Maps an item type to its icon. Shared by the character sheet and the shop. */
fun iconForItemType(type: String): Int = when (type) {
    "weapon" -> R.drawable.ic_item_weapon
    "armor" -> R.drawable.ic_item_armor
    "ring" -> R.drawable.ic_item_ring
    "consumable" -> R.drawable.ic_item_consumable
    else -> R.drawable.ic_item_generic
}
