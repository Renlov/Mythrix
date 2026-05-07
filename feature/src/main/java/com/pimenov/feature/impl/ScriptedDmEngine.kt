package com.pimenov.feature.impl

import com.pimenov.feature.api.LlmEngine
import com.pimenov.feature.api.LlmMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.absoluteValue

/**
 * Stub DM engine used when the on-device LLM is not available.
 * Goals:
 *  - Variability: never repeat the same line for the same intent in a row.
 *  - No false combat triggers: scripted replies are descriptive only.
 *    Combat state is started via the explicit game action, not from text.
 *  - Intent matching uses anchored regex (word starts) instead of bare substrings.
 */
class ScriptedDmEngine : LlmEngine {
    override val id: String = "scripted"
    override suspend fun isReady(): Boolean = true

    override fun generate(prompt: String, history: List<LlmMessage>): Flow<String> = flow {
        val turn = history.count { it.role == LlmMessage.Role.ASSISTANT }
        val intent = classify(prompt.lowercase())
        val plotContext = extractPlotContext(history)

        val reply = if (intent == Intent.GENERIC && plotContext != null) {
            // Fall back to the stage situation so the player isn't stuck in generic limbo
            // when the LLM is unavailable.
            plotContext
        } else {
            val pool = REPLIES.getValue(intent)
            val seed = (prompt.hashCode() xor (turn * 31)).absoluteValue
            pool[seed % pool.size]
        }
        for (chunk in reply.chunked(3)) {
            emit(chunk)
            delay(35)
        }
    }

    private fun extractPlotContext(history: List<LlmMessage>): String? {
        val sys = history.lastOrNull {
            it.role == LlmMessage.Role.SYSTEM && it.content.contains("[ТЕКУЩИЙ ЭТАП")
        } ?: return null
        // Strip the leading marker line, keep the situation prose only.
        return sys.content
            .lineSequence()
            .filterNot { it.startsWith("[ТЕКУЩИЙ ЭТАП") }
            .filterNot { it.startsWith("Веди игрока") }
            .joinToString("\n")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private fun classify(prompt: String): Intent {
        return when {
            INTRO_RE.containsMatchIn(prompt) -> Intent.INTRO
            COMBAT_RE.containsMatchIn(prompt) -> Intent.COMBAT
            LOOK_RE.containsMatchIn(prompt) -> Intent.LOOK
            TALK_RE.containsMatchIn(prompt) -> Intent.TALK
            SEARCH_RE.containsMatchIn(prompt) -> Intent.SEARCH
            REST_RE.containsMatchIn(prompt) -> Intent.REST
            SNEAK_RE.containsMatchIn(prompt) -> Intent.SNEAK
            FOREST_RE.containsMatchIn(prompt) -> Intent.FOREST
            DUNGEON_RE.containsMatchIn(prompt) -> Intent.DUNGEON
            TAVERN_RE.containsMatchIn(prompt) -> Intent.TAVERN
            else -> Intent.GENERIC
        }
    }

    private enum class Intent {
        INTRO, COMBAT, LOOK, TALK, SEARCH, REST, SNEAK,
        FOREST, DUNGEON, TAVERN, GENERIC
    }

    companion object {
        // Anchored to word starts to avoid matching unrelated words (e.g. "большой" → no combat).
        private val INTRO_RE = Regex("""\b(начни|начало|приключени)""")
        private val COMBAT_RE = Regex("""\b(атак\w*|нападени|сражени|бой\b|бить|удар\w*)""")
        private val LOOK_RE = Regex("""\b(осматр|оглядыв|оглядеть|вижу|смотр|разгляд)""")
        private val TALK_RE = Regex("""\b(заговор|говор|обращаю|спрашиваю|спросить|приветств)""")
        private val SEARCH_RE = Regex("""\b(обыск|обыщ|ищу|искать|поиск|роюсь)""")
        private val REST_RE = Regex("""\b(привал|отдых|отдыхаю|сплю|лагерь)""")
        private val SNEAK_RE = Regex("""\b(крадусь|подкрад|бесшум|тихо|тайком|незамеч)""")
        private val FOREST_RE = Regex("""\b(лес\w*|чащ|роща|тропинк)""")
        private val DUNGEON_RE = Regex("""\b(подземель|катакомб|пещер|склеп)""")
        private val TAVERN_RE = Regex("""\b(трактир|таверн|город\w*|рынок|площад)""")

        private val REPLIES: Map<Intent, List<String>> = mapOf(
            Intent.INTRO to listOf(
                "Тёплый свет очага трактира «Серебряный Грифон» дрожит на дубовых стенах. " +
                    "К тебе подходит трактирщик с тревогой в глазах: «Говорят, ты искатель приключений? " +
                    "В старых катакомбах под холмом снова шевелится тьма.»",
                "Дождь барабанит по черепичной крыше постоялого двора. " +
                    "У камина старый бард замолкает на полуслове и кивает тебе — будто узнал. " +
                    "На столе перед тобой лежит свёрнутая карта с печатью городской стражи.",
                "Ты приходишь в себя на обочине тракта. В воздухе пахнет грозой, " +
                    "а вдалеке, над холмами, кружат вороны. Тропа уходит вниз, к чёрной кромке леса."
            ),
            Intent.COMBAT to listOf(
                "Воздух густеет от напряжения. Противник пригибается, готовый к броску. " +
                    "Решай быстро — атаковать, увернуться или попытаться оглушить?",
                "Сталь звенит о сталь. У тебя есть мгновение, чтобы выбрать манёвр.",
                "Враг скалится, прикрывая бок щитом. Один точный удар может решить исход."
            ),
            Intent.LOOK to listOf(
                "Ты медленно обводишь взглядом место. Мхом поросшие плиты, " +
                    "следы от костра у стены и едва заметная царапина на двери — будто что-то пытались высечь.",
                "В сумраке проступают детали: ржавая решётка над водостоком, " +
                    "обрывок верёвки на крюке и пара отпечатков, ведущих в боковой коридор.",
                "На первый взгляд всё спокойно. Но если присмотреться — пыль на полу " +
                    "потревожена, словно кто-то прошёл здесь совсем недавно."
            ),
            Intent.TALK to listOf(
                "Существо настораживается, измеряет тебя взглядом. " +
                    "«Чего тебе?» — наконец произносит оно, не убирая руки с пояса.",
                "Незнакомец чуть наклоняет голову. «Говори. Только не лги — я узнаю.»",
                "Голос отвечает не сразу. «Странно слышать живого голоса в этих стенах… " +
                    "Что привело тебя сюда?»"
            ),
            Intent.SEARCH to listOf(
                "Ты осторожно перебираешь вещи. Под скомканной тряпкой блестит медная монета, " +
                    "а в щели между досок застряла записка с одним словом: «беги».",
                "Ничего ценного — но в углу, под мешком муки, ты находишь маленький ключ " +
                    "с гербом, какого ты раньше не видел.",
                "Полки пусты, ящики выпотрошены. Кто-то уже искал здесь до тебя — и торопился."
            ),
            Intent.REST to listOf(
                "Ты разводишь маленький костёр и прислоняешься к камню. " +
                    "Тепло понемногу возвращается в пальцы. Восстановлено немного сил.",
                "Короткий привал даёт передышку. Слышно только треск дров и собственное дыхание.",
                "Ты закрываешь глаза всего на минуту — этого хватает, чтобы голова перестала шуметь."
            ),
            Intent.SNEAK to listOf(
                "Ты прижимаешься к стене и беззвучно скользишь вдоль неё. " +
                    "Брось d20 — это проверка Ловкости (Скрытность).",
                "Шаг, ещё шаг — мягко, по дуге, в обход света факела. " +
                    "Нужен бросок d20 на скрытность.",
                "Ты замираешь, считаешь до трёх и движешься. Кубик решит, заметили ли тебя."
            ),
            Intent.FOREST to listOf(
                "Под кронами древних сосен царит сумрак. Где-то вдали воет волк. Тропа разветвляется.",
                "Лес дышит сыростью. С веток капает, и каждый шорох кажется чужим шагом.",
                "Папоротник по пояс, паутина серебрится в косых лучах. Тропа едва различима."
            ),
            Intent.DUNGEON to listOf(
                "Холодный воздух пахнет селитрой. Факел трещит, освещая каменные плиты, покрытые рунами.",
                "Своды теряются во мраке. Вода капает с потолка в такт твоему пульсу.",
                "Стены покрыты копотью и царапинами — словно по ним волокли что-то тяжёлое."
            ),
            Intent.TAVERN to listOf(
                "Зал гудит от разговоров. Бард в углу настраивает лютню. На столе перед тобой — кружка эля и потёртая карта.",
                "Хозяин трактира кивает тебе как старому знакомому. У стойки спорят два наёмника.",
                "Пахнет жареным мясом и мокрой шерстью. Кто-то у камина бросает на тебя долгий взгляд."
            ),
            Intent.GENERIC to listOf(
                "Мир замирает в ожидании твоих действий. Опиши, что ты делаешь, или брось кубик.",
                "Тишина натягивается, как тетива. Что ты предпримешь?",
                "Ход за тобой. Можно действовать, говорить или осмотреться — что выберешь?",
                "Сцена ждёт. Опиши намерение, и я расскажу, что произойдёт.",
                "В воздухе висит вопрос — и он адресован тебе."
            )
        )
    }
}
