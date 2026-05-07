package com.pimenov.main.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
fun ModelDownloadScreen(
    onDone: () -> Unit,
    viewModel: ModelDownloadViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) { if (state.completed) onDone() }

    FantasyBackground(tag = SceneTag.DUNGEON) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                strRes("model_download_title"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(12.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        strRes("model_download_subtitle"),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    if (state.inProgress) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("${(state.progress * 100).toInt()}%")
                    }
                    state.error?.let {
                        Text("Ошибка: $it", color = MaterialTheme.colorScheme.tertiary)
                    }
                    Spacer(Modifier.height(16.dp))
                    if (!state.inProgress) {
                        PrimaryActionButton(
                            label = if (state.error != null) strRes("model_download_retry")
                            else strRes("model_download_start"),
                            onClick = viewModel::start,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        PrimaryActionButton(
                            label = "Пропустить (использовать офлайн-режим)",
                            onClick = onDone,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun strRes(name: String): String {
    val ctx = LocalContext.current
    val id = ctx.resources.getIdentifier(name, "string", ctx.packageName)
    return if (id != 0) ctx.getString(id) else name
}
