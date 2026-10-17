# MediaKeeper

**Baixe vídeos do TikTok, Instagram, YouTube/Shorts e Facebook direto no celular — sem marca d'água.**

[![Licença: GPL v3](https://img.shields.io/badge/licen%C3%A7a-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3ddc84.svg)
[![Compilar APK](https://github.com/blackzedjhin1/mediakeeper/actions/workflows/build-apk.yml/badge.svg)](https://github.com/blackzedjhin1/mediakeeper/actions/workflows/build-apk.yml)

MediaKeeper é um aplicativo Android de código aberto para salvar vídeos das principais redes sociais no próprio aparelho. Foi desenvolvido e testado para o **Samsung Galaxy A12**, mas funciona em qualquer Android 8.0 ou superior.

Todo o processamento acontece **dentro do celular**. O motor de download é o [yt-dlp](https://github.com/yt-dlp/yt-dlp) — o mesmo utilizado em computadores — empacotado para Android pela biblioteca [youtubedl-android](https://github.com/yausername/youtubedl-android), com o FFmpeg cuidando da junção de vídeo e áudio.

---

## Instalação

### Pelo celular (mais simples)

1. Abra este link no navegador do celular:

   **https://github.com/blackzedjhin1/mediakeeper/releases/latest/download/mediakeeper.apk**

2. O Android vai avisar que a instalação de fontes desconhecidas está bloqueada. Toque em **Configurações** e ative **Permitir desta fonte**.
3. Toque em **Instalar**. Se o Play Protect aparecer, escolha **Instalar mesmo assim** — é o comportamento normal para qualquer app distribuído fora da Play Store.
4. Na primeira abertura, aguarde alguns segundos enquanto o app extrai o Python e o FFmpeg (isso acontece uma única vez).

> O APK tem cerca de 100 MB porque carrega o Python e o FFmpeg embutidos. É o preço de não depender de servidor nenhum.

### Pelo computador

Baixe o APK no link acima e transfira para o celular por cabo USB, Google Drive ou Telegram. Depois abra o arquivo pelo **Meus Arquivos**.

---

## Recursos

| | |
|---|---|
| **Sem marca d'água** | O app descarta de propósito as versões carimbadas pelo TikTok |
| **Quatro plataformas** | TikTok, Instagram, YouTube/Shorts e Facebook |
| **Qualidade à escolha** | Melhor qualidade, Full HD, HD, 480p ou só áudio em MP3 |
| **Segundo plano** | O download continua com o app minimizado ou a tela apagada, com progresso na barra de notificações |
| **Fila organizada** | Os downloads rodam um por vez, para não estourar a memória do aparelho |
| **Histórico** | A lista de arquivos já baixados abre junto com o app, com abrir, compartilhar e excluir |
| **Compartilhar link** | Mande o link direto do TikTok/Instagram/YouTube para o MediaKeeper pelo menu "Compartilhar" |
| **Sem anúncios** | Nenhuma propaganda, nenhum rastreador, nenhuma conta para criar |

---

## Como usar

1. Abra o vídeo no TikTok, Instagram, YouTube ou Facebook.
2. Toque em **Compartilhar → Compartilhar link** (ou em "Copiar link").
3. Abra o **MediaKeeper**: se você compartilhou, o link já chega preenchido; se copiou, toque em **Colar**.
4. Escolha a qualidade e toque em **BAIXAR VÍDEO**.
5. Acompanhe o progresso. Ao terminar, o arquivo aparece na lista **Baixados** e na pasta `Download/MediaKeeper` do aparelho (e também na Galeria).

Os arquivos ficam em `Armazenamento interno → Download → MediaKeeper`.

---

## Download em segundo plano

Assim que o download começa, aparece uma notificação com o progresso e o botão **Cancelar**. Você pode minimizar o app, usar o celular ou apagar a tela — o download continua. Ao terminar, chega uma notificação **Download concluído** que abre o app quando tocada.

O MediaKeeper usa um **serviço em primeiro plano** (`dataSync`) e um **wake lock** parcial para o Android não suspender a rede no meio da transferência.

> **Exceção:** fechar o app pela lista de recentes encerra o processo e o download junto. Isso é uma regra do Android 14+, não uma escolha do app. Para baixar em segundo plano, apenas minimize (botão Início).

---

## Sobre a marca d'água

- **TikTok** — é a única plataforma que carimba o arquivo. O yt-dlp expõe duas versões: uma sem marca (`play_addr`) e outra com o selo do TikTok (`download_addr`). O MediaKeeper pede a primeira e ainda aplica o filtro `format_id!*=download`, que descarta a versão marcada.
- **Instagram, Facebook e YouTube** — o arquivo original não tem marca d'água, então o download já sai limpo.

O filtro nunca quebra o download: se algum site não tiver nenhum formato compatível, o app cai automaticamente no formato padrão.

---

## Instagram e Facebook exigem cookies

Boa parte do conteúdo do Instagram e do Facebook só é liberada para quem está logado. Para resolver isso:

1. No **computador**, instale a extensão **Get cookies.txt LOCALLY** (Chrome ou Firefox).
2. Entre no Instagram (ou Facebook) com a sua conta.
3. Exporte os cookies do site e salve o arquivo `cookies.txt`.
4. Mande o arquivo para o celular.
5. No app, toque em **Cookies** e selecione o arquivo.

O `cookies.txt` fica guardado na área privada do app, que nenhum outro aplicativo consegue ler, e é usado apenas para autenticar o próprio download.

> Use isso apenas com a sua conta, exatamente como você já faz no navegador do computador.

---

## Manutenção: mantenha o motor atualizado

As plataformas mudam com frequência e podem quebrar os downloads de um dia para o outro. Quem corrige isso é o time do yt-dlp, várias vezes por semana.

Por isso existe o botão **Atualizar motor** no rodapé do app: ele baixa a versão mais recente do yt-dlp direto do GitHub e substitui a que veio no APK.

**Se algum vídeo parar de baixar, toque em Atualizar motor antes de qualquer outra coisa.** É o que resolve a maioria dos casos, sem precisar reinstalar o app.

---

## Privacidade

- O MediaKeeper **não coleta, não armazena e não envia** nenhum dado pessoal.
- **Não há anúncios**, rastreadores, analytics nem servidor próprio.
- As únicas conexões feitas pelo app são: os sites das plataformas (para baixar o vídeo) e o GitHub (para atualizar o yt-dlp).
- Os arquivos baixados e o `cookies.txt` ficam somente no seu aparelho.

---

## Limitações conhecidas

- Os downloads acontecem **um por vez**. Cada download abre um processo Python com o yt-dlp dentro; rodar vários ao mesmo tempo estoura a memória de aparelhos com 3 GB, como o Galaxy A12. Se você mandar três links, o segundo só começa quando o primeiro terminar.
- O **YouTube** mudou bastante e alguns vídeos podem exigir login. Nesse caso, importe os cookies do `youtube.com` pelo botão **Cookies**.
- Vídeos **privados**, com **restrição de idade**, de **contas privadas** ou protegidos por **DRM** (Netflix, Disney+, Globoplay) não são suportados. É uma limitação do próprio yt-dlp.
- Um **carrossel** do Instagram baixa apenas o primeiro vídeo do post, porque o app usa `--no-playlist` para não baixar mais do que você pediu.

---

## Problemas comuns

| Problema | Solução |
|---|---|
| "Requested format is not available" | Toque em **Atualizar motor**. |
| "Sign in to confirm you're not a bot" (YouTube) | Importe os cookies do `youtube.com` pelo botão **Cookies**. |
| Login necessário no Instagram/Facebook | Importe os cookies, como descrito acima. |
| "Não deu para salvar" / erro de armazenamento | **Configurações → Apps → MediaKeeper → Permissões** e libere **Arquivos e mídia**. No Android 10, ative também "Permitir gerenciar todos os arquivos" se a opção aparecer. |
| A notificação não mostra o progresso | **Configurações → Apps → MediaKeeper → Notificações** e permita as notificações. O download funciona igual, só não mostra o andamento fora do app. |
| O download parou ao fechar o app nos recentes | Regra do Android 14+: fechar pelas recentes encerra o app. Minimize em vez de fechar. |
| O app demora alguns segundos para abrir | Normal na **primeira** abertura: está extraindo o Python e o FFmpeg. Nas seguintes, abre na hora. |
| O arquivo não aparece na Galeria | Abra pela lista **Baixados** do app. Alguns aparelhos levam um tempo para indexar; reiniciar o celular força a atualização. |
| Baixou só o áudio no TikTok | Escolha **Melhor qualidade**. No modo MP3 o app prioriza a faixa de áudio do vídeo e alguns vídeos só têm a música de fundo. |

---

## Estrutura do projeto

```
MediaKeeper/
├─ .github/workflows/build-apk.yml   compila e publica o APK no GitHub Actions
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle.properties
└─ app/
   ├─ build.gradle.kts
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ java/br/com/mediakeeper/app/
      │  ├─ App.kt              inicialização do motor (yt-dlp + FFmpeg)
      │  ├─ Presets.kt          qualidades e o filtro "sem marca d'água"
      │  ├─ Downloads.kt        fila, progresso, arquivos e histórico
      │  ├─ DownloadService.kt  serviço em primeiro plano (segundo plano)
      │  ├─ HistoryAdapter.kt   lista de downloads
      │  └─ MainActivity.kt     tela principal
      └─ res/                   telas, cores, tema e ícone
```

Detalhes técnicos:

- Motor: `io.github.junkfood02.youtubedl-android:library:0.18.1` + `:ffmpeg:0.18.1` (Maven Central).
- `minSdk 26`, `targetSdk 34`, Kotlin 1.9.24, AGP 8.5.2, Gradle 8.7, JDK 17.
- A fila roda em um `CoroutineScope` ligado ao processo, protegida por um **serviço em primeiro plano** (`dataSync`) e por um **wake lock** parcial.
- Saída em `Download/MediaKeeper` — um dos diretórios acessíveis mesmo com o armazenamento restrito do Android 11+.

---

## Compilando

O repositório inclui um fluxo do **GitHub Actions** que compila e publica o APK automaticamente a cada envio de código para a branch `main`. Não é preciso instalar nada no computador.

Para compilar localmente:

1. Instale o **Android Studio** (Ladybug ou mais recente).
2. Em **File → Open**, selecione a pasta do projeto.
3. Aguarde a sincronização do Gradle (ele baixa as dependências sozinho).
4. Em **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

---

## Licença

Distribuído sob a **GNU General Public License v3.0**. Veja [LICENSE](LICENSE).

O MediaKeeper usa a biblioteca [youtubedl-android](https://github.com/yausername/youtubedl-android), também licenciada sob GPL-3.0, e o [yt-dlp](https://github.com/yt-dlp/yt-dlp), de domínio público (Unlicense).

Copyright (C) 2026 blackzedjhin1

---

## Aviso legal

O MediaKeeper é uma ferramenta pessoal construída sobre software livre. Utilize-o apenas para baixar conteúdo que você tem o direito de baixar: seus próprios vídeos, material de domínio público ou conteúdo cuja licença permita o download.

Respeite os direitos autorais e os termos de uso das plataformas. Não redistribua conteúdo de terceiros sem autorização do autor.
