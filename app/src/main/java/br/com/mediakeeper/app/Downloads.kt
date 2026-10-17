package br.com.mediakeeper.app

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class JobStatus { QUEUED, RUNNING, DONE, ERROR, CANCELED }

data class DownloadJob(
    val id: String,
    val title: String,
    val status: JobStatus,
    val progress: Int = 0,
    val detail: String = "",
    val file: File? = null,
)

/**
 * Fila + historico de downloads.
 *
 * Os downloads rodam **um por vez**, de proposito: cada download abre um
 * processo de Python com o yt-dlp dentro, e rodar varios ao mesmo tempo
 * derruba a memoria de um aparelho como o Galaxy A12.
 *
 * O trabalho acontece no escopo do processo ([Engine.scope]) e o
 * [DownloadService] mantem esse processo vivo quando o app esta minimizado.
 */
object Downloads {

    private class Task(val id: String, val url: String, val preset: Preset)

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events

    private val queue = Channel<Task>(Channel.UNLIMITED)

    @Volatile
    private var worker: Job? = null

    fun notify(message: String) {
        _events.tryEmit(message)
    }

    /** Pasta publica onde os arquivos ficam: Download/MediaKeeper */
    fun folder(): File {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(base, "MediaKeeper")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun cookiesFile(context: Context): File = File(context.filesDir, "cookies.txt")

    fun hasCookies(context: Context): Boolean =
        cookiesFile(context).let { it.exists() && it.length() > 0L }

    /** Le a pasta de downloads para o app ja abrir com o historico cheio. */
    fun loadHistory() {
        val keep = _jobs.value.filter { it.status != JobStatus.DONE }
        val known = keep.mapNotNull { it.file?.absolutePath }.toSet()
        val found = folder().listFiles()
            ?.filter { isMedia(it) && it.absolutePath !in known }
            ?.sortedByDescending { it.lastModified() }
            ?.map { fileJob(it) }
            .orEmpty()
        _jobs.value = keep + found
    }

    /** Coloca o link na fila e garante que o servico esta de pe. */
    fun start(context: Context, url: String, preset: Preset) {
        val id = UUID.randomUUID().toString()
        val job = DownloadJob(
            id = id,
            title = "Na fila...",
            status = JobStatus.QUEUED,
            detail = "Aguardando a vez"
        )
        _jobs.update { listOf(job) + it }

        ensureWorker()
        queue.trySend(Task(id, url, preset))
        DownloadService.start(context)
    }

    /**
     * Cancela um download. Se ainda estiver na fila, basta marcar; se ja
     * estiver rodando, mata o processo do yt-dlp (o que faz o download
     * lancar [YoutubeDL.CanceledException]).
     */
    fun cancel(id: String) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        when (job.status) {
            JobStatus.RUNNING -> runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
            JobStatus.QUEUED -> patch(id) { it.copy(status = JobStatus.CANCELED, detail = "Cancelado") }
            else -> Unit
        }
    }

    fun remove(id: String) {
        _jobs.update { list -> list.filterNot { it.id == id } }
    }

    fun delete(context: Context, file: File): Boolean {
        val ok = runCatching { file.delete() }.getOrDefault(false)
        if (ok) {
            _jobs.update { list -> list.filterNot { it.file?.absolutePath == file.absolutePath } }
            runCatching {
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            }
        }
        return ok
    }

    /** Cria a fila de trabalho uma unica vez. */
    @Synchronized
    private fun ensureWorker() {
        if (worker?.isActive == true) return
        worker = Engine.scope.launch {
            for (task in queue) {
                val job = _jobs.value.firstOrNull { it.id == task.id }
                // Foi cancelado ou removido enquanto esperava na fila.
                if (job == null || job.status != JobStatus.QUEUED) continue
                runTask(task)
            }
        }
    }

    private suspend fun runTask(task: Task) {
        patch(task.id) { it.copy(status = JobStatus.RUNNING, detail = "Conectando ao site") }

        try {
            if (!Engine.await()) {
                fail(task.id, "O motor não iniciou: ${Engine.lastError}")
                return
            }

            val context = Engine.appContext
            if (context == null) {
                fail(task.id, "Contexto do app indisponível.")
                return
            }

            val outDir = folder()
            val startedAt = System.currentTimeMillis() - 3000L
            val request = buildRequest(context, task.url, task.preset, outDir)

            YoutubeDL.getInstance().execute(request, task.id, false) { progress, _eta, line ->
                val text = describe(line)
                patch(task.id) { job ->
                    job.copy(
                        progress = progress.toInt().coerceIn(0, 100),
                        detail = text.ifBlank { job.detail },
                        title = directoryTitle(line) ?: job.title
                    )
                }
            }

            val file = newestFile(outDir, startedAt)
            if (file == null) {
                fail(task.id, "O download terminou mas o arquivo não foi encontrado.")
                return
            }

            runCatching {
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            }

            patch(task.id) { job ->
                job.copy(
                    title = file.nameWithoutExtension,
                    status = JobStatus.DONE,
                    progress = 100,
                    detail = humanSize(file.length()),
                    file = file
                )
            }
            notify("Salvo em Download/MediaKeeper: ${file.name}")
        } catch (canceled: YoutubeDL.CanceledException) {
            patch(task.id) { it.copy(status = JobStatus.CANCELED, detail = "Cancelado") }
        } catch (t: Throwable) {
            fail(task.id, readable(t))
        }
    }

    private fun buildRequest(
        context: Context,
        url: String,
        preset: Preset,
        outDir: File
    ): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
        request.addOption("--no-mtime")
        request.addOption("--newline")
        request.addOption("--no-playlist")
        request.addOption("--trim-filenames", 120)
        request.addOption("--concurrent-fragments", 4)
        request.addOption("--retries", 10)
        request.addOption("--fragment-retries", 10)
        request.addOption("--socket-timeout", 30)
        request.addOption("-o", File(outDir, "%(title)s [%(id)s].%(ext)s").absolutePath)

        val cookies = cookiesFile(context)
        if (cookies.exists() && cookies.length() > 0L) {
            request.addOption("--cookies", cookies.absolutePath)
        }

        preset.apply(request)
        return request
    }

    private fun patch(id: String, block: (DownloadJob) -> DownloadJob) {
        _jobs.update { list ->
            val index = list.indexOfFirst { it.id == id }
            if (index < 0) return@update list
            val updated = block(list[index])
            if (updated == list[index]) return@update list
            list.toMutableList().also { it[index] = updated }
        }
    }

    private fun fail(id: String, message: String) {
        patch(id) { it.copy(status = JobStatus.ERROR, detail = message) }
        notify("Falhou: $message")
    }

    private fun isMedia(file: File): Boolean {
        if (!file.isFile) return false
        val name = file.name.lowercase(Locale.ROOT)
        if (name.startsWith(".")) return false
        return !name.endsWith(".part") && !name.endsWith(".ytdl") && !name.endsWith(".temp")
    }

    private fun newestFile(dir: File, since: Long): File? =
        dir.listFiles()
            ?.filter { isMedia(it) && it.lastModified() >= since }
            ?.maxByOrNull { it.lastModified() }

    private fun fileJob(file: File) = DownloadJob(
        id = "arquivo:" + file.absolutePath,
        title = file.nameWithoutExtension,
        status = JobStatus.DONE,
        progress = 100,
        detail = "${humanSize(file.length())} - ${dateFormat.format(Date(file.lastModified()))}",
        file = file
    )

    /** Deixa a linha do yt-dlp curta e em portugues para mostrar na tela. */
    private fun describe(line: String): String {
        val text = line.trim()
        return when {
            text.isEmpty() -> ""
            text.contains("Destination:") -> "Baixando..."
            text.contains("Merging formats") -> "Juntando vídeo e áudio..."
            text.contains("ExtractAudio") -> "Convertendo para MP3..."
            text.contains("has already been downloaded") -> "Este arquivo já foi baixado antes."
            text.contains("Deleting original file") -> "Finalizando..."
            text.contains("ETA") -> text.removePrefix("[download]").trim().take(70)
            text.startsWith("[") -> ""
            else -> text.take(70)
        }
    }

    /** Aproveita a linha "Destination:" para ja mostrar o nome do arquivo. */
    private fun directoryTitle(line: String): String? {
        val marker = "Destination:"
        val index = line.indexOf(marker)
        if (index < 0) return null
        val path = line.substring(index + marker.length).trim()
        if (path.isEmpty()) return null
        return File(path).nameWithoutExtension.takeIf { it.isNotBlank() }
    }

    fun humanSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "$bytes B" else String.format(Locale.ROOT, "%.1f %s", value, units[unit])
    }
}
