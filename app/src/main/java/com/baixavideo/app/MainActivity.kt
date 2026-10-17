package com.baixavideo.app

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tilUrl: TextInputLayout
    private lateinit var etUrl: TextInputEditText
    private lateinit var spinnerQuality: Spinner
    private lateinit var cardCurrent: View
    private lateinit var tvCurrentTitle: TextView
    private lateinit var progressCurrent: LinearProgressIndicator
    private lateinit var tvCurrentStatus: TextView
    private lateinit var btnCancelCurrent: MaterialButton
    private lateinit var tvEngine: TextView
    private lateinit var adapter: HistoryAdapter

    private var pendingUrl: String? = null
    private var downloadingId: String? = null

    private val linkRegex = Regex("https?://\\S+")

    private val pickCookies =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val target = Downloads.cookiesFile(this@MainActivity)
                        contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        } ?: throw IllegalStateException("arquivo vazio")
                    }.isSuccess
                }
                toast(if (ok) "Cookies importados com sucesso" else "Não consegui ler esse arquivo de cookies")
            }
        }

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val askStorage =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val url = pendingUrl
            pendingUrl = null
            if (granted && url != null) startDownload(url)
            else toast("Sem permissão de armazenamento não dá para salvar o vídeo")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupSpinner()
        setupHistory()
        setupClicks()
        observeState()
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        Downloads.loadHistory()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun bindViews() {
        tilUrl = findViewById(R.id.tilUrl)
        etUrl = findViewById(R.id.etUrl)
        spinnerQuality = findViewById(R.id.spinnerQuality)
        cardCurrent = findViewById(R.id.cardCurrent)
        tvCurrentTitle = findViewById(R.id.tvCurrentTitle)
        progressCurrent = findViewById(R.id.progressCurrent)
        tvCurrentStatus = findViewById(R.id.tvCurrentStatus)
        btnCancelCurrent = findViewById(R.id.btnCancelCurrent)
        tvEngine = findViewById(R.id.tvEngine)
    }

    private fun setupSpinner() {
        val labels = Preset.values().map { it.label }
        spinnerQuality.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            labels
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun setupHistory() {
        val list = findViewById<RecyclerView>(R.id.rvHistory)
        list.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter(
            onOpen = { openFile(it) },
            onShare = { shareFile(it) },
            onDelete = { confirmDelete(it) },
            onCancel = { Downloads.cancel(it) },
            onDismiss = { Downloads.remove(it) }
        )
        list.adapter = adapter
    }

    private fun setupClicks() {
        findViewById<MaterialButton>(R.id.btnDownload).setOnClickListener { onDownloadClicked() }
        findViewById<MaterialButton>(R.id.btnPaste).setOnClickListener { pasteFromClipboard() }
        findViewById<MaterialButton>(R.id.btnUpdateEngine).setOnClickListener { updateEngine() }
        findViewById<MaterialButton>(R.id.btnCookies).setOnClickListener { pickCookies.launch(arrayOf("*/*")) }
        btnCancelCurrent.setOnClickListener { downloadingId?.let { Downloads.cancel(it) } }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    Downloads.jobs.collect { jobs ->
                        adapter.submit(jobs)
                        val active = jobs.firstOrNull { it.status == JobStatus.RUNNING }
                            ?: jobs.firstOrNull { it.status == JobStatus.QUEUED }
                        downloadingId = active?.id
                        if (active == null) {
                            cardCurrent.visibility = View.GONE
                        } else {
                            cardCurrent.visibility = View.VISIBLE
                            tvCurrentTitle.text = active.title
                            progressCurrent.setProgressCompat(active.progress, true)
                            tvCurrentStatus.text = if (active.status == JobStatus.QUEUED) {
                                "Na fila - aguardando a vez"
                            } else {
                                active.detail.ifBlank { "${active.progress}%" }
                            }
                        }
                    }
                }
                launch {
                    Downloads.events.collect { toast(it) }
                }
                launch {
                    while (true) {
                        tvEngine.text = engineStatus()
                        delay(700)
                    }
                }
            }
        }
    }

    private fun engineStatus(): String = when {
        Engine.lastError != null -> "Erro no motor: ${Engine.lastError}"
        Engine.booting -> "Preparando o motor (só na primeira vez, aguarde)..."
        else -> "Motor pronto - yt-dlp ${Engine.version ?: "?"}"
    }

    private fun onDownloadClicked() {
        val url = extractUrl(etUrl.text?.toString())
        if (url == null) {
            tilUrl.error = getString(R.string.error_url)
            return
        }
        tilUrl.error = null

        // Necessario para a barra de progresso aparecer na notificacao (Android 13+).
        // Se o usuario recusar, o download continua funcionando normalmente.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingUrl = url
                askStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        startDownload(url)
    }

    private fun startDownload(url: String) {
        val presets = Preset.values()
        val index = spinnerQuality.selectedItemPosition.coerceIn(0, presets.size - 1)
        val preset = presets[index]

        if (isLoginWall(url) && !Downloads.hasCookies(this)) {
            toast("Dica: Instagram/Facebook podem pedir o arquivo de cookies (botão Cookies)")
        }

        Downloads.start(this, url, preset)
        etUrl.setText("")
        hideKeyboard()
    }

    private fun isLoginWall(url: String): Boolean =
        url.contains("instagram", true) || url.contains("facebook", true) ||
            url.contains("tiktok.com/@", true)

    private fun pasteFromClipboard() {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = manager?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()

        val url = extractUrl(text)
        if (url == null) {
            toast("Não achei nenhum link copiado")
        } else {
            etUrl.setText(url)
            toast("Link colado")
        }
    }

    private fun updateEngine() {
        toast("Verificando a versão mais nova do motor...")
        Engine.scope.launch { Downloads.notify(Engine.update(this@MainActivity)) }
    }

    private fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return linkRegex.find(text.trim())?.value?.trimEnd('.', ',', ')', ']', '"', '\'')
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("text/") != true) return
        val url = extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
        if (url != null) {
            etUrl.setText(url)
            toast("Link recebido")
        }
    }

    private fun openFile(file: File) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uriFor(file), mimeOf(file))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        } catch (t: Throwable) {
            toast("Nenhum app conseguiu abrir este arquivo")
        }
    }

    private fun shareFile(file: File) {
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeOf(file)
                putExtra(Intent.EXTRA_STREAM, uriFor(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, file.name))
        } catch (t: Throwable) {
            toast("Não deu para compartilhar este arquivo")
        }
    }

    private fun confirmDelete(file: File) {
        MaterialAlertDialogBuilder(this)
            .setTitle(file.name)
            .setMessage("Apagar este arquivo do celular?")
            .setNegativeButton("Não", null)
            .setPositiveButton("Apagar") { _, _ ->
                toast(
                    if (Downloads.delete(this, file)) "Arquivo apagado"
                    else "Não consegui apagar o arquivo"
                )
            }
            .show()
    }

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

    private fun mimeOf(file: File): String = when (file.extension.lowercase(Locale.ROOT)) {
        "mp3", "m4a", "aac", "opus", "ogg", "wav" -> "audio/*"
        else -> "video/*"
    }

    private fun hideKeyboard() {
        val view = currentFocus ?: return
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
