package com.pimenov.character.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pimenov.character.api.CharClass
import com.pimenov.character.api.CharacterSheet
import com.pimenov.character.api.Race
import com.pimenov.character.api.Stats

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val race: String,
    val charClass: String,
    val str: Int,
    val dex: Int,
    val con: Int,
    val intl: Int,
    val wis: Int,
    val cha: Int,
    val maxHp: Int,
    val currentHp: Int,
    val ac: Int,
    val attackBonus: Int,
    val damageDie: Int
) {
    fun toDomain(): CharacterSheet = CharacterSheet(
        id = id,
        name = name,
        race = Race.valueOf(race),
        charClass = CharClass.valueOf(charClass),
        stats = Stats(str, dex, con, intl, wis, cha),
        maxHp = maxHp,
        currentHp = currentHp,
        ac = ac,
        attackBonus = attackBonus,
        damageDie = damageDie
    )

    companion object {
        fun fromDomain(s: CharacterSheet): CharacterEntity = CharacterEntity(
            id = s.id,
            name = s.name,
            race = s.race.name,
            charClass = s.charClass.name,
            str = s.stats.str,
            dex = s.stats.dex,
            con = s.stats.con,
            intl = s.stats.int,
            wis = s.stats.wis,
            cha = s.stats.cha,
            maxHp = s.maxHp,
            currentHp = s.currentHp,
            ac = s.ac,
            attackBonus = s.attackBonus,
            damageDie = s.damageDie
        )
    }
}
