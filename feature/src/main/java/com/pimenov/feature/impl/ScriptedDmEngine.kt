package com.pimenov.feature.impl

import com.pimenov.feature.api.LlmEngine
import com.pimenov.feature.api.LlmMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ScriptedDmEngine : LlmEngine {
    override val id: String = "scripted"
    override suspend fun isReady(): Boolean = true

    override fun generate(prompt: String, history: List<LlmMessage>): Flow<String> = flow {
        val reply = pickReply(prompt.lowercase(), history.size)
        for (chunk in reply.chunked(3)) {
            emit(chunk)
            delay(35)
        }
    }

    private fun pickReply(prompt: String, turn: Int): String {
        return when {
            turn == 0 -> "Тёплый свет очага трактира «Серебряный Грифон» дрожит на дубовых стенах. " +
                    "К тебе подходит трактирщик с тревогой в глазах: «Говорят, ты искатель приключений? " +
                    "В старых катакомбах под холмом снова шевелится тьма. Награда — сто золотых.»"
            "атак" in prompt || "напад" in prompt || "бо" in prompt && "й" in prompt ->
                "Из тени выступает гоблин с ржавым клинком и шипит. " +
                "Бой начинается — брось d20, чтобы атаковать. (Враг: гоблин, AC 12, HP 8)"
            "город" in prompt || "трактир" in prompt ->
                "Зал гудит от разговоров. Бард в углу настраивает лютню. На столе перед тобой — кружка эля и потёртая карта."
            "лес" in prompt ->
                "Под кронами древних сосен царит сумрак. Где-то вдали воет волк. Тропа разветвляется."
            "подзем" in prompt || "катакомб" in prompt ->
                "Холодный воздух пахнет селитрой. Факел трещит, освещая каменные плиты, покрытые рунами."
            else -> "Мир замирает в ожидании твоих действий. Опиши, что ты делаешь, или брось кубик."
        }
    }
}
