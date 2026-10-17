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

    /** Pasta onde os arquivos ficam. Descoberta uma vez por execucao. */
    @Volatile
    private var outDir: File? = null

    /**
     * Pasta preferida: Download/MediaKeeper, que aparece no gerenciador de
     * arquivos e na galeria.
     *
     * Em varios aparelhos o Android bloqueia a escrita nessa pasta mesmo para
     * o proprio app. Antes de usar, o app testa a gravacao: se falhar, passa a
     * usar a pasta do app (que funciona sempre) e o video nunca se perde.
     */
    fun folder(): File {
        outDir?.let { return it }
        val dir = resolveFolder(Engine.appContext)
        outDir = dir
        return dir
    }

    /** Troca para a pasta interna do app (usada quando Download/ esta bloqueada). */
    fun usePrivateFolder() {
        val context = Engine.appContext ?: return
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(root, "MediaKeeper")
        dir.mkdirs()
        outDir = dir
    }

    /** Nome da pasta atual, para mostrar na tela e nas mensagens. */
    fun folderLabel(): String {
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MediaKeeper"
        )
        return if (folder().absolutePath == publicDir.absolutePath) {
            "Download/MediaKeeper"
        } else {
            "pasta do app"
        }
    }

    private fun resolveFolder(context: Context?): File {
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MediaKeeper"
        )
        if (canWrite(publicDir)) return publicDir

        val root = context?.getExternalFilesDir(null) ?: context?.filesDir
        val fallback = File(root ?: publicDir, "MediaKeeper")
        fallback.mkdirs()
        return fallback
    }

    private fun canWrite(dir: File): Boolean {
        if (!dir.exists() && !dir.mkdirs()) return false
        return runCatching {
            val probe = File(dir, ".mediakeeper_gravacao")
            probe.writeText("ok")
            probe.delete()
        }.isSuccess
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
        patch(task.id) { it.copy(status = JobStatus.RUNNING, detail = "Preparando o motor") }

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

            // O yt-dlp que vem dentro do app envelhece e os sites mudam. Esta
            // atualizacao roda sozinha (uma vez por versao do app), entao o
            // usuario nao precisa apertar nada para o download funcionar.
            Engine.ensureUpdated(context)?.let { aviso ->
                patch(task.id) { it.copy(detail = aviso) }
            }

            var tentativa = 0
            while (true) {
                val outDir = folder()
                val startedAt = System.currentTimeMillis() - 3000L
                val request = buildRequest(context, task.url, task.preset, outDir)

                try {
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
                } catch (canceled: YoutubeDL.CanceledException) {
                    patch(task.id) { it.copy(status = JobStatus.CANCELED, detail = "Cancelado") }
                    return
                } catch (t: Throwable) {
                    // Erro que costuma ser do motor desatualizado: atualiza e
                    // tenta mais uma vez, sem incomodar o usuario.
                    if (tentativa == 0 && podeTentarDeNovo(t)) {
                        tentativa = 1
                        patch(task.id) {
                            it.copy(
                                detail = "Não deu certo. Atualizando o motor e tentando de novo...",
                                progress = 0
                            )
                        }
                        val aviso = Engine.update(context)
                        patch(task.id) { it.copy(detail = aviso) }
                        continue
                    }
                    fail(task.id, explicar(t))
                    return
                }

                val file = newestFile(outDir, startedAt)

                if (file == null && tentativa == 0) {
                    // O Android pode ter bloqueado a pasta Download. A pasta do
                    // proprio app sempre funciona, entao vale uma segunda chance.
                    tentativa = 1
                    usePrivateFolder()
                    patch(task.id) {
                        it.copy(
                            detail = "O sistema bloqueou a pasta Download. Tentando na pasta do app...",
                            progress = 0
                        )
                    }
                    continue
                }

                if (file == null) {
                    fail(
                        task.id,
                        "O download terminou mas não achei o arquivo. Confira o espaço livre no celular."
                    )
                    return
                }

                if (perigoso(file)) {
                    runCatching { file.delete() }
                    fail(task.id, "Arquivo bloqueado por segurança: não é um vídeo ou áudio de verdade.")
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
                        detail = "${humanSize(file.length())} - ${folderLabel()}",
                        file = file
                    )
                }
                notify("Salvo em ${folderLabel()}: ${file.name}")
                return
            }
        } catch (t: Throwable) {
            fail(task.id, explicar(t))
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
        request.addOption("--concurrent-fragments", 3)
        request.addOption("--retries", 10)
        request.addOption("--fragment-retries", 10)
        request.addOption("--extractor-retries", 3)
        request.addOption("--file-access-retries", 3)
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

    /**
     * Barreira de seguranca: o app so guarda video, audio e imagem.
     *
     * Se algum endereco devolver um APK, um .exe ou um script no lugar do
     * video, o arquivo e apagado na hora e o usuario avisado. Video de verdade
     * nunca comeca com esses bytes, entao nada legitimo e recusado.
     */
    private fun perigoso(file: File): Boolean {
        val nome = file.name.lowercase(Locale.ROOT)
        val bloqueadas = listOf(
            ".apk", ".exe", ".dex", ".so", ".jar", ".bat", ".cmd", ".sh",
            ".js", ".vbs", ".msi", ".zip", ".rar", ".7z", ".scr", ".com"
        )
        if (bloqueadas.any { nome.endsWith(it) }) return true

        val cabecalho = ByteArray(8)
        val lidos = runCatching { file.inputStream().use { it.read(cabecalho) } }.getOrDefault(0)
        if (lidos < 2) return false

        fun byte(at: Int): Int = cabecalho[at].toInt() and 0xFF

        return when {
            // MZ: programa do Windows
            byte(0) == 0x4D && byte(1) == 0x5A -> true
            // PK..: zip, APK, JAR
            lidos >= 4 && byte(0) == 0x50 && byte(1) == 0x4B &&
                byte(2) == 0x03 && byte(3) == 0x04 -> true
            // ELF: executavel ou biblioteca do Linux/Android
            lidos >= 4 && byte(0) == 0x7F && byte(1) == 0x45 &&
                byte(2) == 0x4C && byte(3) == 0x46 -> true
            // dex: codigo de app Android
            lidos >= 4 && byte(0) == 0x64 && byte(1) == 0x65 &&
                byte(2) == 0x78 && byte(3) == 0x0A -> true
            // #!: script
            byte(0) == 0x23 && byte(1) == 0x21 -> true
            // CAFEBABE: classe Java
            lidos >= 4 && byte(0) == 0xCA && byte(1) == 0xFE &&
                byte(2) == 0xBA && byte(3) == 0xBE -> true
            else -> false
        }
    }

    /**
     * Vale a pena tentar de novo depois de atualizar o motor? Erro de video
     * privado, removido ou link errado nao melhora com tentativa.
     */
    private fun podeTentarDeNovo(t: Throwable): Boolean {
        val text = readable(t).lowercase(Locale.ROOT)
        val definitivos = listOf(
            "unsupported url", "invalid url", "is not a valid url",
            "private video", "this video is private", "removed by the uploader",
            "video unavailable", "this video is not available",
            "no video formats found", "requested format is not available",
            "confirm your age", "age-restricted", "not available in your country",
            "account has been terminated", "incomplete youtube id"
        )
        return definitivos.none { text.contains(it) }
    }

    /** Traduz o erro do yt-dlp para uma frase curta com o que fazer. */
    private fun explicar(t: Throwable): String {
        val texto = readable(t)
        val chave = texto.lowercase(Locale.ROOT)
        return when {
            chave.contains("sign in to confirm") || chave.contains("not a bot") ||
                chave.contains("cookies") || chave.contains("login required") ||
                chave.contains("rate-limit") || chave.contains("rate limit") ||
                chave.contains("requires authentication") ->
                "O site pediu login para liberar este vídeo. Toque em \"Cookies\" e importe o arquivo cookies.txt do seu navegador: $texto"

            chave.contains("too many requests") || chave.contains("429") ->
                "O site bloqueou por excesso de tentativas. Espere alguns minutos e tente de novo."

            chave.contains("private") ->
                "Este vídeo é privado e não pode ser baixado."

            chave.contains("removed") || chave.contains("unavailable") ->
                "Este vídeo foi removido ou não está mais disponível no site."

            chave.contains("unsupported url") || chave.contains("invalid url") ->
                "O app não reconhece esse link. Use o endereço do vídeo (TikTok, Instagram, YouTube ou Facebook)."

            chave.contains("failed to resolve") || chave.contains("unable to resolve") ||
                chave.contains("temporary failure") || chave.contains("timed out") ||
                chave.contains("connection") || chave.contains("network") ->
                "Sem conexão com o site. Confira a internet do celular e tente de novo."

            chave.contains("no space") || chave.contains("enospc") ||
                chave.contains("permission denied") || chave.contains("errno 13") ->
                "Não consegui gravar o arquivo. Libere espaço no celular e tente de novo."

            chave.contains("requested format is not available") ->
                "Este vídeo não tem a qualidade escolhida. Tente a opção \"Melhor qualidade\"."

            else -> texto
        }
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
