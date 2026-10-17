package com.baixavideo.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servico em primeiro plano que mantem o download vivo com o app minimizado.
 *
 * Ele nao baixa nada por conta propria: quem baixa e a fila em [Downloads],
 * que vive no escopo do processo. O papel deste servico e:
 *
 *  1. segurar o processo (com um app em primeiro plano o Android nao mata),
 *  2. manter a CPU acordada com um wake lock parcial enquanto baixa,
 *  3. mostrar progresso e o botao "Cancelar" na barra de notificacoes.
 *
 * Ele se encerra sozinho assim que a fila zera.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var inForeground = false
    private var lastKey: String? = null

    /** Ultimo job que realmente rodou aqui, para avisar quando termina. */
    private var lastRunningId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cancelId = if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_JOB_ID)
        } else {
            null
        }

        ensureObserver()

        // Obrigacao do Android: um servico criado com startForegroundService()
        // PRECISA virar foreground em poucos segundos, senao o sistema derruba o
        // processo. Por isso isto vem antes de qualquer verificacao: se a fila
        // estiver vazia, o push() logo abaixo ja encerra tudo.
        if (!inForeground) {
            val active = activeJobs()
            val current = active.firstOrNull { it.status == JobStatus.RUNNING }
                ?: active.firstOrNull()
            startInForeground(build(current, active.size))
        }

        cancelId?.let { Downloads.cancel(it) }
        push()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observer = null
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureObserver() {
        if (observer?.isActive == true) return
        observer = scope.launch {
            Downloads.jobs.collect { push() }
        }
    }

    private fun activeJobs(): List<DownloadJob> =
        Downloads.jobs.value.filter {
            it.status == JobStatus.RUNNING || it.status == JobStatus.QUEUED
        }

    /** Atualiza (ou encerra) a notificacao conforme o estado da fila. */
    private fun push() {
        if (!inForeground) return

        val active = activeJobs()
        if (active.isEmpty()) {
            finish()
            return
        }

        val current = active.firstOrNull { it.status == JobStatus.RUNNING } ?: active.first()
        if (current.status == JobStatus.RUNNING) lastRunningId = current.id

        // Evita reescrever a notificacao varias vezes por segundo.
        val key = "${current.id}|${current.status}|${current.progress}|${current.title}|${active.size}"
        if (key == lastKey) return
        lastKey = key

        acquireWakeLock()
        post(NOTIFICATION_ID, build(current, active.size))
    }

    private fun finish() {
        releaseWakeLock()
        lastKey = null
        if (inForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            inForeground = false
        }

        // Deixa um aviso tocavel de que o download terminou.
        val done = lastRunningId?.let { id ->
            Downloads.jobs.value.firstOrNull { it.id == id && it.status == JobStatus.DONE }
        }
        if (done != null) post(COMPLETE_ID, buildCompleted(done))

        lastRunningId = null
        stopSelf()
    }

    private fun build(job: DownloadJob?, count: Int): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (job == null) {
            return builder
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Preparando o download...")
                .setProgress(0, 0, true)
                .build()
        }

        val queueInfo = if (count > 1) " ($count na fila)" else ""
        builder.setContentTitle(job.title.take(60))

        if (job.status == JobStatus.RUNNING) {
            builder.setContentText((job.detail.ifBlank { "Baixando" } + queueInfo).take(90))
            builder.setProgress(100, job.progress, false)
            val cancel = PendingIntent.getService(
                this,
                1,
                Intent(this, DownloadService::class.java)
                    .setAction(ACTION_CANCEL)
                    .putExtra(EXTRA_JOB_ID, job.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, getString(R.string.cancel), cancel)
        } else {
            builder.setContentText("Na fila para baixar$queueInfo")
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun buildCompleted(job: DownloadJob): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentIntent(openAppIntent())
            .setContentTitle("Download concluído")
            .setContentText(job.title.take(70))
            .setAutoCancel(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        inForeground = true
    }

    private fun post(id: Int, notification: Notification) {
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(id, notification)
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_downloads),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progresso dos downloads em andamento"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Sem isso a rede pode parar quando a tela apaga e o aparelho entra em
     * suspensao, deixando o download "parado" no meio do caminho.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            runCatching { acquire(WAKE_LOCK_TIMEOUT_MS) }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 41
        private const val COMPLETE_ID = 42
        private const val ACTION_CANCEL = "com.baixavideo.app.action.CANCEL"
        private const val EXTRA_JOB_ID = "job_id"
        private const val WAKE_LOCK_TAG = "BaixaVideo:download"
        private const val WAKE_LOCK_TIMEOUT_MS = 60L * 60L * 1000L

        /**
         * Sobe o servico. Se o sistema recusar (por exemplo, o app ja esta em
         * segundo plano numa versao nova do Android), o download continua
         * mesmo assim: ele so perde a protecao extra.
         */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DownloadService::class.java)
                )
            }
        }
    }
}
