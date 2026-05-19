package com.pimenov.feature.impl

import com.pimenov.feature.BuildConfig
import com.pimenov.feature.api.LlmEngine
import com.pimenov.feature.api.LlmMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DeepSeek Chat Completions — OpenAI-compatible streaming.
 * Endpoint: https://api.deepseek.com/v1/chat/completions
 * Models: `deepseek-chat` (V3, быстрый), `deepseek-reasoner` (R1, размышляет дольше).
 * System prompt provider is injected so the engine stays agnostic of which
 * world/plot is active.
 */
class DeepSeekLlmEngine(
    private val systemPromptProvider: suspend () -> String,
    private val model: String = DEFAULT_MODEL,
    private val apiKey: String = BuildConfig.DEEPSEEK_API_KEY,
) : LlmEngine {

    override val id: String = "deepseek:$model"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 90_000
        }
        expectSuccess = false
    }

    override suspend fun isReady(): Boolean = apiKey.isNotBlank()

    override fun generate(userPrompt: String, history: List<LlmMessage>): Flow<String> = flow {
        if (apiKey.isBlank()) {
            emit("[Ошибка] DEEPSEEK_API_KEY не задан в local.properties. Положи `deepseek.api.key=sk-...` и пересобери.")
            return@flow
        }

        val systemPrompt = systemPromptProvider()
        val messages = buildList {
            add(ApiMessage(role = "system", content = systemPrompt))
            history.forEach { msg ->
                val role = when (msg.role) {
                    LlmMessage.Role.SYSTEM -> "system"
                    LlmMessage.Role.USER -> "user"
                    LlmMessage.Role.ASSISTANT -> "assistant"
                }
                add(ApiMessage(role = role, content = msg.content))
            }
            add(ApiMessage(role = "user", content = userPrompt))
        }

        val body = ChatRequest(
            model = model,
            messages = messages,
            stream = true,
            temperature = 0.8,
            maxTokens = 800,
        )

        client.preparePost(ENDPOINT) {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(body)
        }.execute { response ->
            if (response.status != HttpStatusCode.OK) {
                val text = runCatching { response.bodyAsText() }.getOrDefault("")
                emit("[Ошибка ${response.status.value}] $text")
                return@execute
            }
            val channel: ByteReadChannel = response.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                if (payload == "[DONE]") break
                val delta = runCatching {
                    json.decodeFromString(StreamChunk.serializer(), payload)
                        .choices.firstOrNull()?.delta?.content
                }.getOrNull() ?: continue
                if (delta.isNotEmpty()) emit(delta)
            }
        }
    }

    companion object {
        private const val ENDPOINT = "https://api.deepseek.com/v1/chat/completions"
        /** DeepSeek V3 — основной чат-эндпоинт. */
        const val DEFAULT_MODEL = "deepseek-chat"
    }
}

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean,
    val temperature: Double,
    @SerialName("max_tokens") val maxTokens: Int,
)

@Serializable
private data class ApiMessage(val role: String, val content: String)

@Serializable
private data class StreamChunk(val choices: List<Choice> = emptyList()) {
    @Serializable
    data class Choice(val delta: Delta = Delta())

    @Serializable
    data class Delta(val content: String = "")
}
