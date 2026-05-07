package com.pimenov.character.api

enum class Race { HUMAN, ELF, DWARF, HALFLING }
enum class CharClass { FIGHTER, ROGUE, WIZARD, CLERIC }

data class Stats(
    val str: Int,
    val dex: Int,
    val con: Int,
    val int: Int,
    val wis: Int,
    val cha: Int
) {
    fun mod(value: Int): Int = (value - 10) / 2
    val strMod get() = mod(str)
    val dexMod get() = mod(dex)
    val conMod get() = mod(con)
    val intMod get() = mod(int)
    val wisMod get() = mod(wis)
    val chaMod get() = mod(cha)
}

data class CharacterSheet(
    val id: Long = 0L,
    val name: String,
    val race: Race,
    val charClass: CharClass,
    val stats: Stats,
    val maxHp: Int,
    val currentHp: Int,
    val ac: Int,
    val attackBonus: Int,
    val damageDie: Int
) {
    companion object {
        fun new(name: String, race: Race, charClass: CharClass): CharacterSheet {
            val stats = defaultStats(charClass)
            val maxHp = baseHp(charClass) + stats.conMod
            return CharacterSheet(
                name = name,
                race = race,
                charClass = charClass,
                stats = stats,
                maxHp = maxHp,
                currentHp = maxHp,
                ac = baseAc(charClass) + stats.dexMod,
                attackBonus = attackMod(charClass, stats),
                damageDie = damageDie(charClass)
            )
        }

        private fun defaultStats(c: CharClass): Stats = when (c) {
            CharClass.FIGHTER -> Stats(16, 12, 14, 10, 11, 10)
            CharClass.ROGUE -> Stats(10, 16, 12, 13, 12, 10)
            CharClass.WIZARD -> Stats(8, 13, 12, 16, 12, 10)
            CharClass.CLERIC -> Stats(12, 10, 13, 10, 16, 12)
        }

        private fun baseHp(c: CharClass): Int = when (c) {
            CharClass.FIGHTER -> 12
            CharClass.ROGUE -> 9
            CharClass.WIZARD -> 7
            CharClass.CLERIC -> 10
        }

        private fun baseAc(c: CharClass): Int = when (c) {
            CharClass.FIGHTER -> 16
            CharClass.ROGUE -> 13
            CharClass.WIZARD -> 11
            CharClass.CLERIC -> 15
        }

        private fun damageDie(c: CharClass): Int = when (c) {
            CharClass.FIGHTER -> 8
            CharClass.ROGUE -> 6
            CharClass.WIZARD -> 6
            CharClass.CLERIC -> 8
        }

        private fun attackMod(c: CharClass, s: Stats): Int = when (c) {
            CharClass.FIGHTER -> 2 + s.strMod
            CharClass.ROGUE -> 2 + s.dexMod
            CharClass.WIZARD -> 2 + s.intMod
            CharClass.CLERIC -> 2 + s.wisMod
        }
    }
}
