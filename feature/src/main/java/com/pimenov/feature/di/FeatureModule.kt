package com.pimenov.feature.di

import com.pimenov.feature.api.LlmEngine
import com.pimenov.feature.api.TurnLogger
import com.pimenov.feature.game.GameStateRepository
import com.pimenov.feature.game.world.AssetStoryContentSource
import com.pimenov.feature.game.world.ClassCatalog
import com.pimenov.feature.game.world.StoryContentSource
import com.pimenov.feature.game.world.StoryRules
import com.pimenov.feature.game.world.WorldCatalog
import com.pimenov.feature.impl.DeepSeekLlmEngine
import com.pimenov.feature.impl.FirebaseTurnLogger
import com.pimenov.feature.world.WorldBibleLoader
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.UUID

// Active story for the pilot. Bundled in the APK under assets/world/<id>/.
// [FIREBASE] Позже выбор сюжета (включая скачанные) приходит из манифеста
// Firebase, а source становится AssetStoryContentSource ИЛИ FileStoryContentSource
// в зависимости от того, bundled сюжет или скачанный. Остальной граф не меняется.
private const val ACTIVE_STORY_ID = "tavern"
private const val PROMPT_TEMPLATE_PATH = "prompts/dm_system_v1.txt"

fun featureModule() = module {
    single<StoryContentSource> { AssetStoryContentSource(androidContext(), storyId = ACTIVE_STORY_ID) }
    single { WorldCatalog.load(get()) }
    single { StoryRules.load(get()) }
    single { ClassCatalog.load(get()) }
    single { GameStateRepository(androidContext(), get(), get(), get(), get(), defaultName = "Кейн") }
    // One logging session per app launch. Best-effort; no-op until Firebase is set up.
    single<TurnLogger> { FirebaseTurnLogger(sessionId = UUID.randomUUID().toString()) }
    single<LlmEngine> {
        val game = get<GameStateRepository>()
        val catalog = get<WorldCatalog>()
        val template = androidContext().assets
            .open(PROMPT_TEMPLATE_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val loader = WorldBibleLoader(source = get(), rules = get(), promptTemplate = template)
        DeepSeekLlmEngine(
            systemPromptProvider = {
                loader.buildSystemPrompt(game.state.value, game.journal(), catalog)
            },
            logger = get(),
        )
    }
}
