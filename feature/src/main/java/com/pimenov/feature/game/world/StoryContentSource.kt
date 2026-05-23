package com.pimenov.feature.game.world

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Source of one story's content files (player.json, items.json, locations.json,
 * enemies.json, npcs.json, quests.json, rules.json). Decouples the engine from
 * *where* a story lives: bundled in the APK or downloaded to private storage.
 *
 * A story is the unit of content: same file names, story-specific ids. Loading
 * code asks for files by short name and never knows the physical location.
 */
interface StoryContentSource {
    /** Stable id of the story (e.g. "tavern"). Used to namespace save files. */
    val storyId: String

    fun read(fileName: String): String

    fun exists(fileName: String): Boolean

    companion object {
        // [FIREBASE] Версия формата JSON сюжета. Бандл из Firebase с большим
        // schemaVersion в manifest.json считается несовместимым с этим билдом.
        // Поднимать при любом ломающем изменении структуры world/rules JSON.
        const val SCHEMA_VERSION = 1
    }
}

/** Bundled story shipped inside the APK under `assets/world/<storyId>/`. */
class AssetStoryContentSource(
    private val context: Context,
    override val storyId: String,
) : StoryContentSource {

    private val dir = "world/$storyId"

    override fun read(fileName: String): String =
        try {
            context.assets.open("$dir/$fileName").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            throw IllegalStateException("Story asset not found: $dir/$fileName", e)
        }

    override fun exists(fileName: String): Boolean =
        runCatching { context.assets.open("$dir/$fileName").close() }.isSuccess
}

// [FIREBASE] Точка подключения скачанных сюжетов. Бандл сюжета (те же JSON +
// rules.json + manifest.json) скачивается из Firebase Storage и распаковывается
// в filesDir/stories/<storyId>/. Дальше движок читает его ровно как bundled —
// через тот же StoryContentSource, без изменений в WorldCatalog/загрузчике.
//
// Перед использованием скачанного сюжета проверять manifest.json:
//   schemaVersion <= StoryContentSource.SCHEMA_VERSION  (иначе парсер несовместим)
// Несовместимые сюжеты не качать / прятать в списке (см. ответ про раскатку).
class FileStoryContentSource(
    override val storyId: String,
    private val dir: File,
) : StoryContentSource {

    override fun read(fileName: String): String {
        val file = File(dir, fileName)
        if (!file.exists()) throw IllegalStateException("Story file not found: ${file.path}")
        return file.readText(Charsets.UTF_8)
    }

    override fun exists(fileName: String): Boolean = File(dir, fileName).exists()
}
