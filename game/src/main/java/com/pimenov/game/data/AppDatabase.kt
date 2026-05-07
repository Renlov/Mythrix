package com.pimenov.game.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pimenov.character.data.CharacterDao
import com.pimenov.character.data.CharacterEntity

@Database(
    entities = [CharacterEntity::class, GameSaveEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun gameSaveDao(): GameSaveDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "mythrix.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
