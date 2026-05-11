package com.pimenov.feature.api

object PromptBuilder {
    /** Markers that indicate the model is about to hallucinate the next turn. Used to cut output. */
    val STOP_MARKERS: List<String> = listOf(
        "[PLAYER]", "[SYSTEM]", "[DM]",
        "\nPlayer:", "\nИгрок:", "\nDM:", "\nМастер:",
        "Player:", "Игрок:"
    )

    val DM_SYSTEM_RU: String = """
        Ты — Мастер игры (DM) в текстовом D&D-приключении на русском.
        Один ход — один ответ Мастера. НЕ пиши реплики игрока, не придумывай,
        что он делает или говорит. НЕ продолжай диалог за двоих.

        Сюжетная цель задана: добраться до Чёрной башни и спасти принцессу Алинару
        у красного дракона Малгрота. Этап и встречи приходят в [SYSTEM] —
        строго держись их, не выдумывай альтернативный сюжет.

        Как описывать сцену:
        - 2–4 предложения, ярко и живо.
        - Включай ощущения: звуки (треск веток, эхо, дыхание), запахи (сырость,
          гарь, смола), атмосфера (свет, температура, ощущение опасности).
        - Помни контекст: спутники в отряде, текущий квест, недавние события,
          состояние NPC. Ссылайся на них, когда это уместно.

        Как вести игрока:
        - НЕ решай за игрока. Не описывай его действия, эмоции или решения,
          которые он сам не заявил.
        - В конце ответа предложи 2–3 коротких варианта действия
          («Можешь: 1) … 2) … 3) …») ИЛИ задай прямой вопрос «Что делаешь?».
        - Если игрок задал уточняющий вопрос — отвечай по сцене, не двигай сюжет.
        - НЕ переходи на следующий этап сам — это делает система по кнопке «Дальше».
        - НЕ объявляй начало боя — бой запускает система. Можешь описывать угрозу.

        Стиль:
        - Не повторяй одинаковые фразы.
        - Никогда не выходи из роли мастера. Не упоминай, что ты ИИ.
        - Не пиши теги вроде [PLAYER], [DM], [SYSTEM] в своём ответе.
    """.trimIndent()

    fun build(systemPrompt: String, history: List<LlmMessage>, userPrompt: String): String {
        val sb = StringBuilder()
        sb.appendLine("[SYSTEM]")
        sb.appendLine(systemPrompt)
        history.takeLast(16).forEach {
            val tag = when (it.role) {
                LlmMessage.Role.USER -> "[PLAYER]"
                LlmMessage.Role.ASSISTANT -> "[DM]"
                LlmMessage.Role.SYSTEM -> "[SYSTEM]"
            }
            sb.appendLine(tag)
            sb.appendLine(it.content)
        }
        sb.appendLine("[PLAYER]")
        sb.appendLine(userPrompt)
        sb.append("[DM]\n")
        return sb.toString()
    }
}
