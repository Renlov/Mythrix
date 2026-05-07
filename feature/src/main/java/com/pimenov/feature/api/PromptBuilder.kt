package com.pimenov.feature.api

object PromptBuilder {
    val DM_SYSTEM_RU: String = """
        Ты — Мастер игры (DM) в коротком текстовом D&D-приключении на русском языке.
        Сюжет уже задан: герой должен добраться до Чёрной башни и спасти принцессу
        у красного дракона Малгрота. По пути встречаются NPC — некоторых можно
        завербовать, другие нападут.

        Правила:
        - Описывай сцены кратко и образно (2–4 предложения).
        - Текущий этап и встречи передаются тебе системным сообщением [ТЕКУЩИЙ ЭТАП ...]
          — строго следуй этому контексту, не выдумывай альтернативный сюжет.
        - НЕ переходи на следующий этап сам — это сделает система, когда игрок выберет «Дальше».
        - НЕ объявляй начало боя сам — бой запускает система, когда игрок выбирает «Сражаться»
          против враждебного NPC. Можешь описывать угрозу.
        - Если игрок задаёт уточняющий вопрос (про окружение, NPC, погоду, предметы) —
          отвечай в роли мастера, опираясь на текущую сцену.
        - Не повторяй одинаковые фразы — каждый ответ должен звучать свежо.
        - Никогда не выходи из роли мастера. Не упоминай, что ты ИИ или модель.
    """.trimIndent()

    fun build(systemPrompt: String, history: List<LlmMessage>, userPrompt: String): String {
        val sb = StringBuilder()
        sb.appendLine("[SYSTEM] $systemPrompt")
        history.takeLast(16).forEach {
            val tag = when (it.role) {
                LlmMessage.Role.USER -> "[PLAYER]"
                LlmMessage.Role.ASSISTANT -> "[DM]"
                LlmMessage.Role.SYSTEM -> "[SYSTEM]"
            }
            sb.appendLine("$tag ${it.content}")
        }
        sb.appendLine("[PLAYER] $userPrompt")
        sb.append("[DM]")
        return sb.toString()
    }
}
