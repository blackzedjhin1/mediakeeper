package br.com.mediakeeper.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 *
 * O yt-dlp que vem dentro da biblioteca e antigo: quando TikTok, Instagram,
 * YouTube ou Facebook mudam alguma coisa, ele simplesmente para de baixar.
 * Por isso o motor se atualiza sozinho ([ensureUpdated]), em segundo plano e
 * uma vez por versao instalada - o usuario nao precisa apertar nenhum botao.
 */
object Engine {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val ready = CompletableDeferred<Unit>()
    private val updateLock = Mutex()

    private const val PREFS = "mediakeeper"
    private const val KEY_UPDATED_FOR = "motor_atualizado_para"

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

    /** Ultima mensagem sobre a atualizacao do motor (aparece na tela). */
    @Volatile
    var updateStatus: String? = null
        private set

    @Volatile
    var updating: Boolean = false
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

            // Deixa o motor na ultima versao sem o usuario precisar apertar nada.
            if (lastError == null) {
                runCatching { ensureUpdated(app) }
            }
        }
    }

    /** Espera o motor ficar pronto. Retorna false se a inicializacao falhou. */
    suspend fun await(): Boolean {
        ready.await()
        return lastError == null
    }

    /**
     * Atualiza o yt-dlp automaticamente, mas so uma vez por versao instalada:
     * depois disso a marcacao fica gravada e o app abre na hora.
     */
    suspend fun ensureUpdated(context: Context): String? {
        if (lastError != null) return null
        return updateLock.withLock {
            updateStatus?.let { return@withLock it }

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val appVersion = appVersion(context)
            if (prefs.getString(KEY_UPDATED_FOR, null) == appVersion) return@withLock null

            val message = baixarMotor(context, prefs, appVersion)
            updateStatus = message
            message
        }
    }

    /** Atualizacao forcada: botao "Atualizar motor" e nova tentativa automatica. */
    suspend fun update(context: Context): String {
        if (!await()) return "Motor indisponivel: $lastError"
        return updateLock.withLock {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            baixarMotor(context, prefs, appVersion(context))
        }
    }

    /** Troca o yt-dlp pela versao mais nova publicada no GitHub. */
    private fun baixarMotor(
        context: Context,
        prefs: SharedPreferences,
        appVersion: String
    ): String {
        updating = true
        updateStatus = "Buscando a versão mais nova do motor..."
        return try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context)
            version = YoutubeDL.version(context) ?: version
            // So marca como atualizado quando deu certo: sem internet a
            // tentativa se repete no proximo download.
            prefs.edit().putString(KEY_UPDATED_FOR, appVersion).apply()
            if (status == YoutubeDL.UpdateStatus.DONE) {
                "Motor atualizado (yt-dlp $version)"
            } else {
                "Motor já é o mais novo (yt-dlp $version)"
            }
        } catch (t: Throwable) {
            "Não consegui atualizar o motor: ${readable(t)}"
        } finally {
            updating = false
        }
    }

    private fun appVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    }.getOrDefault("0")
}

/** Transforma um erro gigante do yt-dlp em uma frase legivel. */
internal fun readable(t: Throwable): String {
    val raw = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
    val line = raw.lines().firstOrNull { it.contains("ERROR", ignoreCase = true) }
        ?: raw.lines().firstOrNull { it.isNotBlank() }
        ?: raw
    return line.trim().removePrefix("ERROR:").trim().take(220)
}
