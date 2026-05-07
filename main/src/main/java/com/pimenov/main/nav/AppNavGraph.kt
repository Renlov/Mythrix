package com.pimenov.main.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pimenov.character.presentation.CharacterCreationScreen
import com.pimenov.feature.api.ModelDownloader
import com.pimenov.game.presentation.GameScreen
import com.pimenov.main.presentation.MainShell
import com.pimenov.main.presentation.ModelDownloadScreen
import org.koin.compose.koinInject

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val downloader: ModelDownloader = koinInject()
    var startRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startRoute = if (downloader.isModelPresent()) Routes.MAIN else Routes.MODEL_DOWNLOAD
    }

    val resolved = startRoute ?: return

    NavHost(navController = navController, startDestination = resolved) {
        composable(Routes.MODEL_DOWNLOAD) {
            ModelDownloadScreen(
                onDone = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.MODEL_DOWNLOAD) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable(Routes.MAIN) {
            MainShell(
                onNewGame = { navController.navigate(Routes.CHARACTER_CREATION) },
                onContinue = { navController.navigate(Routes.GAME) },
                onDownloadModel = { navController.navigate(Routes.MODEL_DOWNLOAD) }
            )
        }
        composable(Routes.CHARACTER_CREATION) {
            CharacterCreationScreen(
                onCreated = {
                    navController.popBackStack(Routes.MAIN, inclusive = false)
                }
            )
        }
        composable(Routes.GAME) { GameScreen() }
    }
}
