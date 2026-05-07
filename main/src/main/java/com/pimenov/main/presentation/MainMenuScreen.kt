package com.pimenov.main.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pimenov.game.api.GameRepository
import com.pimenov.uikit.R
import com.pimenov.uikit.components.FantasyBackground
import com.pimenov.uikit.components.PrimaryActionButton
import com.pimenov.uikit.components.SceneTag
import org.koin.compose.koinInject

@Composable
fun MainMenuScreen(
    onNewGame: () -> Unit,
    onContinue: () -> Unit,
    onSettings: () -> Unit
) {
    val gameRepo: GameRepository = koinInject()
    var hasSave by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasSave = gameRepo.loadState() != null
    }

    FantasyBackground(tag = SceneTag.TAVERN) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(140.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = strRes("app_name"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(48.dp))
            PrimaryActionButton(
                label = strRes("menu_new_game"),
                onClick = onNewGame,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            PrimaryActionButton(
                label = strRes("menu_continue"),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasSave
            )
            Spacer(Modifier.height(12.dp))
            PrimaryActionButton(
                label = strRes("menu_settings"),
                onClick = onSettings,
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
