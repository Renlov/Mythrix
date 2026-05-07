package com.pimenov.feature.api

import android.content.Context
import com.pimenov.core.dispatchers.AppDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
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
        filename = "gemma3-1b-it.task",
        url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
        displayName = "Gemma 3 1B (быстрая)",
        sizeMb = 555,
        description = "Лёгкая модель. Меньше памяти, быстрый отклик."
    ),
    POWERFUL(
        filename = "gemma3-4b-it.task",
        url = "https://huggingface.co/litert-community/Gemma3-4B-IT/resolve/main/Gemma3-4B-IT_multi-prefill-seq_q4_ekv2048.task",
        displayName = "Gemma 3 4B (умная)",
        sizeMb = 3100,
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
            val connection = (URL(variant.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            connection.connect()
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

    fun delete(variant: ModelVariant): Boolean =
        fileFor(variant).takeIf { it.exists() }?.delete() ?: false

    fun deleteAll() {
        ModelVariant.entries.forEach { delete(it) }
    }
}
