package com.pimenov.feature.api

object PromptBuilder {
    val DM_SYSTEM_RU: String = """
        Ты — мастер подземелий в упрощённой системе D&D 5e. Веди игру на русском языке.
        Описывай сцены кратко и образно (2-4 предложения). Поддерживай атмосферу тёмного фэнтези.
        Когда нужен бросок кубика — прямо проси игрока бросить (d20, d6, d8) и опиши последствия.
        В бою следуй правилам: d20 + модификатор атаки против AC; при попадании — урон оружия + модификатор силы.
        Никогда не выходи из роли мастера. Не упоминай, что ты ИИ.
    """.trimIndent()

    fun build(systemPrompt: String, history: List<LlmMessage>, userPrompt: String): String {
        val sb = StringBuilder()
        sb.appendLine("[SYSTEM] $systemPrompt")
        history.takeLast(12).forEach {
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
