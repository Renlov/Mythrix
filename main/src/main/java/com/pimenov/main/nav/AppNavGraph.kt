package com.pimenov.main.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pimenov.feature.game.GameStateRepository
import com.pimenov.feature.game.world.ClassCatalog
import com.pimenov.main.presentation.CharacterCreationScreen
import com.pimenov.main.presentation.ChatScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun AppNavGraph() {
    val game: GameStateRepository = koinInject()
    val classes: ClassCatalog = koinInject()

    // No save → start at character creation; otherwise resume the game.
    var creating by remember { mutableStateOf(!game.hasSave()) }
    // Bumped on restart to force a fresh ChatViewModel (new session).
    var sessionKey by remember { mutableIntStateOf(0) }

    if (creating) {
        CharacterCreationScreen(
            classes = classes.all(),
            onCreate = { profile ->
                game.startNewGame(profile)
                sessionKey++
                creating = false
            },
        )
    } else {
        ChatScreen(
            vm = koinViewModel(key = "chat-$sessionKey"),
            onRestart = {
                game.resetGame()
                creating = true
            },
        )
    }
}
