package com.pimenov.feature.api

import android.content.Context
import com.pimenov.core.dispatchers.AppDispatchers
import com.pimenov.feature.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface DownloadEvent {
    data class Progress(val ratio: Float) : DownloadEvent
    data class Done(val file: File) : DownloadEvent
    data class Failed(val error: Throwable) : DownloadEvent
}

enum class ModelVariant(
    val filename: String,
    val url: String,
    val displayName: String,
    val sizeMb: Int,
    val description: String
) {
    LITE(
        filename = "qwen2.5-1.5b-instruct.task",
        url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        displayName = "Qwen 2.5 1.5B (быстрая)",
        sizeMb = 1700,
        description = "Лёгкая модель. Меньше памяти, быстрый отклик."
    ),
    POWERFUL(
        filename = "qwen2.5-3b-instruct.task",
        url = "https://huggingface.co/litert-community/Qwen2.5-3B-Instruct/resolve/main/Qwen2.5-3B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        displayName = "Qwen 2.5 3B (умная)",
        sizeMb = 3300,
        description = "Лучше держит роль DM. Требует больше памяти."
    )
}

class ModelDownloader(
    private val context: Context,
    private val dispatchers: AppDispatchers
) {
    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    fun fileFor(variant: ModelVariant): File = File(modelsDir, variant.filename)

    fun isPresent(variant: ModelVariant): Boolean {
        val f = fileFor(variant)
        return f.exists() && f.length() > 1_000_000
    }

    fun installedVariant(): ModelVariant? =
        ModelVariant.entries.firstOrNull { isPresent(it) }

    fun isModelPresent(): Boolean = installedVariant() != null

    val modelFile: File
        get() = installedVariant()?.let { fileFor(it) } ?: fileFor(ModelVariant.LITE)

    fun download(variant: ModelVariant): Flow<DownloadEvent> = flow {
        val target = fileFor(variant)
        target.parentFile?.mkdirs()
        try {
            val connection = openConnection(URL(variant.url))
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                val msg = when (code) {
                    401, 403 -> "Доступ к модели закрыт. Обратитесь к разработчику приложения."
                    404 -> "Файл модели не найден на сервере."
                    in 500..599 -> "Сервер недоступен. Попробуйте позже."
                    else -> "Не удалось скачать модель (код $code)."
                }
                emit(DownloadEvent.Failed(IOException(msg)))
                return@flow
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            val tmp = File(target.parentFile, "${target.name}.part")
            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var lastEmit = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = ((read.toFloat() / total) * 100).toInt()
                            if (pct != lastEmit) {
                                lastEmit = pct
                                emit(DownloadEvent.Progress((read.toFloat() / total).coerceIn(0f, 1f)))
                            }
                        }
                    }
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            emit(DownloadEvent.Done(target))
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            emit(DownloadEvent.Failed(t))
        }
    }.flowOn(dispatchers.io)

    /**
     * Open a connection with manual redirect handling so the Authorization header
     * (HuggingFace token) is preserved across hops to the CDN.
     * HttpURLConnection drops the Authorization header on redirect by default,
     * which causes 401s on HF download endpoints.
     */
    private fun openConnection(initial: URL, maxRedirects: Int = 5): HttpURLConnection {
        var url = initial
        var redirects = 0
        while (true) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                if (BuildConfig.HF_TOKEN.isNotEmpty()) {
                    setRequestProperty("Authorization", "Bearer ${BuildConfig.HF_TOKEN}")
                }
                setRequestProperty("User-Agent", "Mythrix/1.0")
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < maxRedirects) {
                val location = conn.getHeaderField("Location") ?: return conn
                conn.disconnect()
                url = URL(url, location)
                redirects++
                continue
            }
            return conn
        }
    }

    fun delete(variant: ModelVariant): Boolean =
        fileFor(variant).takeIf { it.exists() }?.delete() ?: false

    fun deleteAll() {
        ModelVariant.entries.forEach { delete(it) }
    }
}
