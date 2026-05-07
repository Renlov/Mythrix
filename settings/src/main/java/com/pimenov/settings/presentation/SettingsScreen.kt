package com.pimenov.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pimenov.uikit.components.FantasyBackground
import com.pimenov.uikit.components.GlassCard
import com.pimenov.uikit.components.PrimaryActionButton
import com.pimenov.uikit.components.SceneTag
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    FantasyBackground(tag = SceneTag.TAVERN) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                strRes("settings_title"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strRes("settings_volume"))
                    Slider(value = state.volume, onValueChange = viewModel::setVolume)
                    Text("${(state.volume * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strRes("settings_language"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.language == "ru",
                            onClick = { viewModel.setLanguage("ru") },
                            label = { Text(strRes("settings_language_ru")) }
                        )
                        FilterChip(
                            selected = state.language == "en",
                            onClick = { viewModel.setLanguage("en") },
                            label = { Text(strRes("settings_language_en")) }
                        )
                    }
                }
            }
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (state.modelPresent) "Модель загружена" else "Модель не загружена",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    PrimaryActionButton(
                        label = strRes("settings_delete_model"),
                        onClick = viewModel::deleteModel,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.modelPresent
                    )
                }
            }
            PrimaryActionButton(
                label = "Назад",
                onClick = onBack,
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
