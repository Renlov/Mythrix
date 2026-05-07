package com.pimenov.main.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pimenov.character.api.CharacterRepository
import com.pimenov.character.api.CharacterSheet
import com.pimenov.game.api.GameRepository
import com.pimenov.game.api.GameSave
import com.pimenov.settings.presentation.SettingsScreen
import com.pimenov.uikit.components.FantasyBackground
import com.pimenov.uikit.components.GlassCard
import com.pimenov.uikit.components.PrimaryActionButton
import com.pimenov.uikit.components.SceneTag
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun MainShell(
    onCreateHero: () -> Unit,
    onStartGame: () -> Unit,
    onDownloadModel: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("⚔") },
                    label = { Text(strRes("tab_plots")) }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("▶") },
                    label = { Text(strRes("tab_continue")) }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Text("♕") },
                    label = { Text(strRes("tab_heroes")) }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Text("⚙") },
                    label = { Text(strRes("tab_settings")) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> PlotsTab(
                    onStartGame = onStartGame,
                    onCreateHero = { tab = 2 }
                )
                1 -> ContinueTab(onContinue = onStartGame)
                2 -> HeroesTab(onCreateHero = onCreateHero)
                else -> SettingsScreen(onBack = null, onDownloadModel = onDownloadModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlotsTab(onStartGame: () -> Unit, onCreateHero: () -> Unit) {
    val characterRepo: CharacterRepository = koinInject()
    val gameRepo: GameRepository = koinInject()
    val heroes by characterRepo.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    FantasyBackground(tag = SceneTag.FOREST) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                strRes("tab_plots"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strRes("plots_default_title"))
                    Text(
                        strRes("plots_default_summary"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PrimaryActionButton(
                        label = strRes("plots_start"),
                        onClick = {
                            if (heroes.isEmpty()) onCreateHero() else sheetOpen = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (heroes.isEmpty()) {
                        Text(
                            strRes("plots_create_hero_first"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    strRes("plots_more_soon"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    strRes("plots_pick_hero"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(heroes, key = { it.id }) { hero ->
                        HeroRow(
                            hero = hero,
                            onClick = {
                                scope.launch {
                                    // Fresh save bound to the picked hero — wipes old chat for a new run.
                                    gameRepo.clearAll()
                                    gameRepo.saveState(
                                        GameSave(
                                            characterId = hero.id,
                                            sceneTag = "tavern"
                                        )
                                    )
                                    sheetOpen = false
                                    onStartGame()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroRow(hero: CharacterSheet, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column {
            Text(
                hero.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                "${localizedRace(hero.race.name)} • ${localizedClass(hero.charClass.name)} • HP ${hero.maxHp} AC ${hero.ac}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContinueTab(onContinue: () -> Unit) {
    val gameRepo: GameRepository = koinInject()
    var hasSave by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasSave = gameRepo.loadState() != null }

    FantasyBackground(tag = SceneTag.TAVERN) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                strRes("tab_continue"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(16.dp))
            if (hasSave) {
                PrimaryActionButton(
                    label = strRes("menu_continue"),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    strRes("continue_no_save"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HeroesTab(onCreateHero: () -> Unit) {
    val characterRepo: CharacterRepository = koinInject()
    val heroes by characterRepo.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    FantasyBackground(tag = SceneTag.DUNGEON) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                strRes("tab_heroes"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            if (heroes.isEmpty()) {
                Text(
                    strRes("heroes_empty"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(heroes, key = { it.id }) { hero -> HeroRow(hero = hero, onClick = {}) }
                }
            }
            PrimaryActionButton(
                label = strRes("heroes_create"),
                onClick = onCreateHero,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun localizedRace(raw: String): String = when (raw) {
    "HUMAN" -> strRes("race_human")
    "ELF" -> strRes("race_elf")
    "DWARF" -> strRes("race_dwarf")
    "HALFLING" -> strRes("race_halfling")
    else -> raw
}

@Composable
private fun localizedClass(raw: String): String = when (raw) {
    "FIGHTER" -> strRes("class_fighter")
    "ROGUE" -> strRes("class_rogue")
    "WIZARD" -> strRes("class_wizard")
    "CLERIC" -> strRes("class_cleric")
    else -> raw
}

@Composable
private fun strRes(name: String): String {
    val ctx = LocalContext.current
    val id = ctx.resources.getIdentifier(name, "string", ctx.packageName)
    return if (id != 0) ctx.getString(id) else name
}
