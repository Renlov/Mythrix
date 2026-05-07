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

class ModelDownloader(
    private val context: Context,
    private val dispatchers: AppDispatchers
) {
    val modelFile: File
        get() = File(context.filesDir, "models/$MODEL_FILENAME")

    fun isModelPresent(): Boolean = modelFile.exists() && modelFile.length() > 1_000_000

    fun download(url: String = MODEL_URL): Flow<DownloadEvent> = flow {
        val target = modelFile
        target.parentFile?.mkdirs()
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
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

    fun delete(): Boolean = modelFile.takeIf { it.exists() }?.delete() ?: false

    companion object {
        const val MODEL_FILENAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        const val MODEL_URL =
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
    }
}
