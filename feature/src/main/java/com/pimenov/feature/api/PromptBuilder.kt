package com.pimenov.feature.api

object PromptBuilder {
    /** Markers that indicate the model is about to hallucinate the next turn. Used to cut output. */
    val STOP_MARKERS: List<String> = listOf(
        "[PLAYER]", "[SYSTEM]", "[DM]",
        "Player:", "Игрок:", "Пользователь:", "User:",
        "DM:", "Мастер:",
        "<|", "<|im_start|>", "<|im_end|>", "<|user|>", "<|assistant|>"
    )

    private val UNICODE_ESCAPE = Regex("""\\u([0-9a-fA-F]{4})""")

    /** Decode stray "\uXXXX" literals that some models emit as text. */
    fun decodeEscapes(text: String): String =
        UNICODE_ESCAPE.replace(text) { m ->
            runCatching { m.groupValues[1].toInt(16).toChar().toString() }
                .getOrDefault(m.value)
        }

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
        - Если игрок задал ВОПРОС (где, кто, что, можно ли, сколько и т.п.) —
          сначала дай конкретный ответ от лица мира/NPC, опираясь на сцену
          и спутников. НЕ возвращай вопрос обратно. Только после ответа можно
          предложить действия.
        - Если игрок описал действие — опиши результат и реакцию мира.
        - В конце ответа предложи 2–3 коротких варианта действия
          («Можешь: 1) … 2) … 3) …») ИЛИ задай уточняющий вопрос по теме.
          НЕ повторяй фразу «Что будешь делать?» — она пуста, замени её на
          конкретный выбор.
        - НЕ переходи на следующий этап сам — переход делает система.
        - НЕ объявляй начало боя — бой запускает система. Можешь описывать угрозу.

        Стиль:
        - Не повторяй одинаковые фразы.
        - Никогда не выходи из роли мастера. Не упоминай, что ты ИИ.
        - Не пиши теги вроде [PLAYER], [DM], [SYSTEM] в своём ответе.
    """.trimIndent()

    /**
     * Qwen 2.5 ChatML format. Qwen-Instruct models expect this exact template;
     * any other shape (e.g. [PLAYER]/[DM]) gives garbage or echo outputs.
     */
    fun build(systemPrompt: String, history: List<LlmMessage>, userPrompt: String): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n").append(systemPrompt).append("<|im_end|>\n")
        // Merge consecutive SYSTEM messages from history into the system block above
        // by emitting them inline before the next user turn, so Qwen still sees them.
        val pending = StringBuilder()
        history.takeLast(16).forEach { msg ->
            when (msg.role) {
                LlmMessage.Role.SYSTEM -> {
                    if (pending.isNotEmpty()) pending.append('\n')
                    pending.append(msg.content)
                }
                LlmMessage.Role.USER -> {
                    sb.append("<|im_start|>user\n")
                    if (pending.isNotEmpty()) {
                        sb.append("[Контекст сцены]\n").append(pending).append("\n\n")
                        pending.clear()
                    }
                    sb.append(msg.content).append("<|im_end|>\n")
                }
                LlmMessage.Role.ASSISTANT -> {
                    sb.append("<|im_start|>assistant\n").append(msg.content).append("<|im_end|>\n")
                }
            }
        }
        sb.append("<|im_start|>user\n")
        if (pending.isNotEmpty()) {
            sb.append("[Контекст сцены]\n").append(pending).append("\n\n")
        }
        sb.append(userPrompt).append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }
}
