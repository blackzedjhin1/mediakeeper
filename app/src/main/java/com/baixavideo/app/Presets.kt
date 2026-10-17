package com.baixavideo.app

import com.yausername.youtubedl_android.YoutubeDLRequest

/**
 * Opcoes de qualidade mostradas no app.
 *
 * Todos os presets de video usam o filtro `format_id!*=download`.
 * Motivo: no TikTok o yt-dlp expoe dois arquivos, um SEM marca d'agua
 * (id `play` / `play_addr`) e outro COM marca d'agua (id `download` /
 * `download_addr`, com `preference: -2`). O filtro joga fora esse segundo
 * e cai no fallback `/b` caso algum site nao tenha formatos com esse padrao,
 * entao a selecao nunca quebra por causa do filtro.
 */
enum class Preset(val label: String) {

    BEST("Melhor qualidade"),
    FHD("Full HD (1080p)"),
    HD("HD (720p)"),
    SD("Econômico (480p)"),
    AUDIO("Só áudio (MP3)");

    fun apply(request: YoutubeDLRequest) {
        when (this) {
            BEST -> {
                request.addOption("-f", "bv*[format_id!*=download]+ba/b[format_id!*=download]/b")
                request.addOption("--merge-output-format", "mp4")
            }
            FHD -> capped(request, 1080)
            HD -> capped(request, 720)
            SD -> capped(request, 480)
            AUDIO -> {
                // `ba[format_id!=audio]`: o TikTok tem uma faixa chamada exatamente
                // "audio" (a musica de fundo). Preferimos a faixa de audio do proprio
                // video para nao perder narracao/voz.
                request.addOption("-f", "ba[format_id!=audio]/b[format_id!*=download]/b")
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "0")
                request.addOption("--embed-metadata")
            }
        }
    }

    private fun capped(request: YoutubeDLRequest, height: Int) {
        request.addOption(
            "-f",
            "bv*[height<=$height][format_id!*=download]+ba" +
                "/b[height<=$height][format_id!*=download]" +
                "/b[height<=$height]" +
                "/b[format_id!*=download]" +
                "/b"
        )
        request.addOption("--merge-output-format", "mp4")
    }
}
