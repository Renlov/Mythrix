package com.pimenov.feature.game.player

/** Player's choices on the character-creation screen, used to seed a new game. */
data class CharacterProfile(
    val name: String,
    val classId: String,
    val avatar: String?,
)
