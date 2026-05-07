package com.pimenov.main.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pimenov.character.api.CharacterRepository
import com.pimenov.game.api.GameRepository
import com.pimenov.settings.presentation.SettingsScreen
import com.pimenov.uikit.components.FantasyBackground
import com.pimenov.uikit.components.GlassCard
import com.pimenov.uikit.components.PrimaryActionButton
import com.pimenov.uikit.components.SceneTag
import org.koin.compose.koinInject

@Composable
fun MainShell(
    onNewGame: () -> Unit,
    onContinue: () -> Unit,
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
                0 -> PlotsTab(onNewGame)
                1 -> ContinueTab(onContinue, onNewGame)
                2 -> HeroesTab(onNewGame)
                else -> SettingsScreen(onBack = null, onDownloadModel = onDownloadModel)
            }
        }
    }
}

@Composable
private fun PlotsTab(onNewGame: () -> Unit) {
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
                        onClick = onNewGame,
                        modifier = Modifier.fillMaxWidth()
                    )
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
}

@Composable
private fun ContinueTab(onContinue: () -> Unit, onNewGame: () -> Unit) {
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
                Spacer(Modifier.height(12.dp))
                PrimaryActionButton(
                    label = strRes("menu_new_game"),
                    onClick = onNewGame,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun HeroesTab(onNewGame: () -> Unit) {
    val characterRepo: CharacterRepository = koinInject()
    val latest by characterRepo.observeLatest().collectAsStateWithLifecycle(initialValue = null)

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
            val current = latest
            if (current == null) {
                Text(
                    strRes("heroes_empty"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            current.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            "${current.race.name} • ${current.charClass.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            PrimaryActionButton(
                label = strRes("heroes_create"),
                onClick = onNewGame,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun strRes(name: String): String {
    val ctx = LocalContext.current
    val id = ctx.resources.getIdentifier(name, "string", ctx.packageName)
    return if (id != 0) ctx.getString(id) else name
}
