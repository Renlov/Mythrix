package com.pimenov.feature.game.world

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A playable class from a story's `classes.json`. Drives the player's starting
 * stats and combat modifiers, so picking a class is a real mechanical choice.
 * [FIREBASE] classes.json — часть бандла сюжета, может приехать из Firebase.
 */
@Serializable
data class ClassDef(
    val id: String,
    val name: String,
    val description: String = "",
    val avatar: String? = null,
    val hp: Int = 12,
    @SerialName("attack_bonus") val attackBonus: Int = 2,
    @SerialName("skill_bonus") val skillBonus: Int = 2,
    @SerialName("base_ac") val baseAc: Int = 10,
    @SerialName("starting_items") val startingItems: List<String> = emptyList(),
)

/** Immutable lookup of the playable classes for a story. */
class ClassCatalog private constructor(private val classes: List<ClassDef>) {

    fun all(): List<ClassDef> = classes

    fun byId(id: String): ClassDef? = classes.firstOrNull { it.id == id }

    companion object {
        private const val FILE = "classes.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun load(source: StoryContentSource): ClassCatalog {
            val list = if (source.exists(FILE)) {
                runCatching { json.decodeFromString<List<ClassDef>>(source.read(FILE)) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            return ClassCatalog(list)
        }
    }
}
