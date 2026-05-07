package com.pimenov.character.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pimenov.character.api.CharClass
import com.pimenov.character.api.CharacterSheet
import com.pimenov.character.api.Race
import com.pimenov.uikit.components.FantasyBackground
import com.pimenov.uikit.components.GlassCard
import com.pimenov.uikit.components.PrimaryActionButton
import com.pimenov.uikit.components.SceneTag
import org.koin.androidx.compose.koinViewModel

@Composable
fun CharacterCreationScreen(
    onCreated: (Long) -> Unit,
    viewModel: CharacterCreationViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.created) { state.created?.let { onCreated(it.id) } }

    FantasyBackground(tag = SceneTag.TAVERN) {
        Image(
            painter = painterResource(com.pimenov.uikit.R.drawable.bg_tavern),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.55f
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = strRes("character_title"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::onName,
                        label = { Text(strRes("character_name")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(strRes("character_race"), color = MaterialTheme.colorScheme.secondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Race.entries) { race ->
                            FilterChip(
                                selected = state.race == race,
                                onClick = { viewModel.onRace(race) },
                                label = { Text(race.localized()) }
                            )
                        }
                    }
                    Text(strRes("character_class"), color = MaterialTheme.colorScheme.secondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CharClass.entries) { c ->
                            FilterChip(
                                selected = state.charClass == c,
                                onClick = { viewModel.onClass(c) },
                                label = { Text(c.localized()) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val preview = remember(state.name, state.race, state.charClass) {
                        CharacterSheet.new(state.name.ifEmpty { "—" }, state.race, state.charClass)
                    }
                    StatsPreview(preview)
                }
            }
            PrimaryActionButton(
                label = strRes("character_create"),
                onClick = viewModel::create,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving
            )
        }
    }
}

@Composable
private fun StatsPreview(s: CharacterSheet) {
    Column {
        Text(
            "HP ${s.maxHp}  AC ${s.ac}  +${s.attackBonus} к атаке  d${s.damageDie} урон",
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "STR ${s.stats.str}  DEX ${s.stats.dex}  CON ${s.stats.con}  INT ${s.stats.int}  WIS ${s.stats.wis}  CHA ${s.stats.cha}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun Race.localized(): String = when (this) {
    Race.HUMAN -> strRes("race_human")
    Race.ELF -> strRes("race_elf")
    Race.DWARF -> strRes("race_dwarf")
    Race.HALFLING -> strRes("race_halfling")
}

@Composable
private fun CharClass.localized(): String = when (this) {
    CharClass.FIGHTER -> strRes("class_fighter")
    CharClass.ROGUE -> strRes("class_rogue")
    CharClass.WIZARD -> strRes("class_wizard")
    CharClass.CLERIC -> strRes("class_cleric")
}

@Composable
internal fun strRes(name: String): String {
    val ctx = LocalContext.current
    val id = ctx.resources.getIdentifier(name, "string", ctx.packageName)
    return if (id != 0) ctx.getString(id) else name
}
