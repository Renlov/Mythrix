package com.pimenov.feature.api

object PromptBuilder {
    /**
     * Markers that indicate the model is about to hallucinate the next turn.
     * Anchored on newline so we don't cut on the role label appearing inside narration
     * (e.g. when DM legitimately writes "игрок видит…").
     */
    val STOP_MARKERS: List<String> = listOf(
        "\nИгрок:", "\nPlayer:", "\nПользователь:", "\nUser:",
        "<|im_end|>", "<|im_start|>", "<|user|>", "<|assistant|>",
        "<|endoftext|>"
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
        - Включай ощущения при попытке осмотреться: звуки (треск веток, эхо, дыхание), запахи (сырость,
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
        - В конце ответа предложи 2–3 коротких конкретных варианта действия
          в формате «Можешь: 1) … 2) … 3) …», привязанных к сцене и NPC.
          Запрещены пустые финалки общего вида — всегда конкретика.
        - НЕ переходи на следующий этап сам — переход делает система.
        - НЕ объявляй начало боя — бой запускает система. Можешь описывать угрозу.

        Стиль:
        - Не повторяй одинаковые фразы.
        - Никогда не выходи из роли мастера. Не упоминай, что ты ИИ.
        - Не пиши теги вроде [PLAYER], [DM], [SYSTEM] в своём ответе.
    """.trimIndent()

    /**
     * Plain conversational text. MediaPipe's .task bundle applies the model's
     * native chat template internally — wrapping the text in ChatML ourselves
     * caused double-templating and empty outputs. We pass system + history + new
     * turn as one user message and let the bundle handle role tagging.
     *
     * History is capped by total character budget (rough token proxy) to keep
     * the prefill within the model's context window.
     */
    private const val HISTORY_CHAR_BUDGET = 4000

    private val EMPTY_TAILS = Regex(
        """\s*(Что будешь делать\??|Что делаешь\??|Твой ход\??|Ход за тобой\??)\s*$""",
        RegexOption.IGNORE_CASE
    )

    /** Strip empty closing prompts so the model doesn't latch onto them as a pattern. */
    private fun sanitize(content: String): String =
        content.replace(EMPTY_TAILS, "").trimEnd()

    fun build(systemPrompt: String, history: List<LlmMessage>, userPrompt: String): String {
        val sb = StringBuilder()
        sb.append(systemPrompt).append("\n\n")

        // Newest-first selection, then reverse to chronological — keeps the
        // most recent context when we hit the budget.
        val selected = mutableListOf<LlmMessage>()
        var budget = HISTORY_CHAR_BUDGET
        for (msg in history.asReversed()) {
            val cost = msg.content.length + 16
            if (cost > budget) break
            selected.add(0, msg)
            budget -= cost
        }

        if (selected.isNotEmpty()) {
            sb.append("Контекст сцены и предыдущие реплики:\n")
            selected.forEach { msg ->
                val tag = when (msg.role) {
                    LlmMessage.Role.USER -> "Игрок"
                    LlmMessage.Role.ASSISTANT -> "Мастер"
                    LlmMessage.Role.SYSTEM -> "Сюжет"
                }
                sb.append(tag).append(": ").append(sanitize(msg.content)).append("\n")
            }
            sb.append("\n")
        }

        sb.append("Игрок: ").append(userPrompt).append("\n")
        sb.append("Мастер:")
        return sb.toString()
    }
}
