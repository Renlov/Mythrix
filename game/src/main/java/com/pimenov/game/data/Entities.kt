package com.pimenov.game.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pimenov.game.api.ChatMessage
import com.pimenov.game.api.MessageAuthor

@Entity(tableName = "game_save")
data class GameSaveEntity(
    @PrimaryKey val id: Long = 1L,
    val characterId: Long,
    val sceneTag: String,
    val combatJson: String?
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val author: String,
    val content: String,
    val timestamp: Long
) {
    fun toDomain(): ChatMessage = ChatMessage(
        id = id,
        author = MessageAuthor.valueOf(author),
        content = content,
        timestamp = timestamp
    )

    companion object {
        fun fromDomain(m: ChatMessage): ChatMessageEntity = ChatMessageEntity(
            id = m.id,
            author = m.author.name,
            content = m.content,
            timestamp = m.timestamp
        )
    }
}
