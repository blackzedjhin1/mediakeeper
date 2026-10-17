package com.baixavideo.app

import android.app.Application
import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Engine.boot(this)
    }

    companion object {
        lateinit var instance: App
    }
}

/**
 * Cuida do motor de download (yt-dlp + python + ffmpeg).
 *
 * A primeira inicializacao extrai o python das bibliotecas nativas e demora
 * alguns segundos. O escopo e do processo inteiro, portanto um download
 * continua rodando mesmo se a tela girar ou a activity for recriada.
 */
object Engine {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val ready = CompletableDeferred<Unit>()

    /** Contexto da aplicacao (seguro de guardar, ao contrario de uma Activity). */
    @Volatile
    var appContext: Context? = null
        private set

    @Volatile
    var booting: Boolean = true
        private set

    @Volatile
    var version: String? = null
        private set

    @Volatile
    var lastError: String? = null
        private set

    fun boot(app: Application) {
        appContext = app.applicationContext
        scope.launch {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                version = YoutubeDL.version(app)
            } catch (t: Throwable) {
                lastError = readable(t)
            } finally {
                booting = false
                ready.complete(Unit)
            }
        }
    }

    /** Espera o motor ficar pronto. Retorna false se a inicializacao falhou. */
    suspend fun await(): Boolean {
        ready.await()
        return lastError == null
    }

    /** Baixa a versao mais nova do yt-dlp do GitHub. */
    suspend fun update(context: Context): String {
        if (!await()) return "Motor indisponível: $lastError"
        return try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context)
            version = YoutubeDL.version(context) ?: version
            if (status == YoutubeDL.UpdateStatus.DONE) {
                "Motor atualizado (yt-dlp $version)"
            } else {
                "O motor já está na última versão (yt-dlp $version)"
            }
        } catch (t: Throwable) {
            "Falha ao atualizar: ${readable(t)}"
        }
    }
}

/** Transforma um erro gigante do yt-dlp em uma frase legivel. */
internal fun readable(t: Throwable): String {
    val raw = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
    val line = raw.lines().firstOrNull { it.contains("ERROR", ignoreCase = true) }
        ?: raw.lines().firstOrNull { it.isNotBlank() }
        ?: raw
    return line.trim().removePrefix("ERROR:").trim().take(220)
}
