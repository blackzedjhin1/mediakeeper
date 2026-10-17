# Baixa Vídeo

App Android para baixar vídeos do **TikTok, Instagram, YouTube/Shorts e Facebook**
direto no celular, **sem marca d'água**.

Feito pensando no **Samsung Galaxy A12** (Android 10 ou superior).

O motor por baixo é o [yt-dlp](https://github.com/yt-dlp/yt-dlp) (o mesmo que os
programas de PC usam, só que rodando dentro do celular) empacotado pela
biblioteca [youtubedl-android](https://github.com/yausername/youtubedl-android),
mais o FFmpeg para juntar vídeo+áudio e converter para MP3.

---

## 1. Como conseguir o APK (sem instalar nada no PC)

O projeto já vem com um robô que compila o APK na nuvem, de graça. Você só
precisa de uma conta no GitHub.

1. Crie uma conta em <https://github.com> (se ainda não tiver).
2. Clique em **New repository**.
   - Nome: `baixa-video`
   - Marque **Public** (pode ser privado também).
   - **Não** marque "Add a README file".
   - Clique em **Create repository**.
3. Na página que abrir, clique no link **uploading an existing file**.
4. Abra a pasta `BaixaVideo` deste projeto no Windows, **selecione todo o
   conteúdo dela** (Ctrl+A) e **arraste para dentro da página do GitHub**.
   - Importante: arraste o **conteúdo** (as pastas `app`, `.github` e os
     arquivos `settings.gradle.kts`, etc.), não a pasta `BaixaVideo` inteira.
   - Se o Windows não mostrar a pasta `.github` (começa com ponto), clique em
     **Exibir → Mostrar → Itens ocultos** no Explorador de Arquivos antes.
   - Se você arrastar a pasta inteira, também funciona (o robô detecta sozinho),
     mas o ideal é o conteúdo na raiz.
5. Clique em **Commit changes**.
6. Vá na aba **Actions**. Deve aparecer "Gerar APK" rodando (bolinha amarela).
   - Se não aparecer nada, clique em **Gerar APK** na lista da esquerda e depois
     em **Run workflow → Run workflow**.
7. Espere de 5 a 15 minutos (a primeira vez é mais demorada).
8. Quando ficar verdinho, clique no nome da execução e, no final da página,
   baixe o arquivo **baixa-video-apk** (vem um `.zip` com o `app-debug.apk`
   dentro).

> Sempre que você mudar algo no código e enviar de novo, o GitHub compila um
> APK novo automaticamente.

---

## 2. Instalar no Galaxy A12

1. Baixe o `.zip` no computador e mande o `app-debug.apk` para o celular
   (cabo USB, Google Drive, Telegram, e-mail...), ou baixe direto pelo
   navegador do celular.
2. Abra o arquivo pelo app **Meus Arquivos**.
3. O Android vai avisar que não pode instalar. Toque em **Configurações** e
   ative **Permitir desta fonte** para o app que está abrindo o arquivo.
4. Toque em **Instalar**. Se o Play Protect reclamar, escolha
   **Instalar mesmo assim** (é normal para apps que não vêm da Play Store).

---

## 3. Como usar

1. Abra o vídeo no TikTok / Instagram / YouTube / Facebook.
2. Toque em **Compartilhar → Compartilhar link** (ou em "Copiar link").
3. Abra o **Baixa Vídeo**. Se você usou "Compartilhar", o link já entra
   preenchido. Se copiou, toque no botão **Colar**.
4. Escolha a qualidade:
   - **Melhor qualidade** — pega o melhor vídeo disponível (recomendado).
   - **Full HD (1080p)** / **HD (720p)** / **Econômico (480p)** — limita a
     resolução para economizar espaço e dados.
   - **Só áudio (MP3)** — extrai apenas o som.
5. Toque em **BAIXAR VÍDEO**.
6. Acompanhe a barra de progresso. Ao terminar, o arquivo aparece na lista
   **Baixados** e em `Download/BaixaVideo` (também aparece na Galeria).
7. Na lista você pode **Abrir**, **Compartilhar** ou **Excluir** cada arquivo.

**Baixando em segundo plano:** assim que o download começa, aparece uma
notificação com o progresso e o botão **Cancelar**. Você pode minimizar o app,
usar o celular ou apagar a tela — o download continua. Ao terminar, chega uma
notificação "Download concluído" que abre o app quando tocada.

**Dica:** o vídeo fica salvo em `Downloads/BaixaVideo` do armazenamento interno.

---

## 4. Sobre a marca d'água

- **TikTok:** o yt-dlp já entrega o arquivo **sem** a marca d'água (o app pede
  o `play_addr`, não o `download_addr` que vem com o selo do TikTok). Além
  disso, o app usa o filtro `format_id!*=download`, que descarta de propósito a
  versão com marca d'água.
- **Instagram, Facebook e YouTube:** os downloads já vêm sem marca d'água
  (essas plataformas não carimbam o arquivo original).

Esse filtro nunca quebra o download: se por acaso um site não tiver nenhum
formato compatível, o app cai no formato normal automaticamente.

---

## 5. Cookies (Instagram e Facebook)

Muita coisa do Instagram e do Facebook só baixa se o site "achar" que você está
logado. Para resolver isso:

1. No **computador**, instale a extensão **Get cookies.txt LOCALLY** no Chrome
   ou Firefox.
2. Entre no Instagram (ou Facebook) logado na sua conta.
3. Com a extensão aberta, exporte os cookies do site
   (`instagram.com`) e salve o arquivo `cookies.txt`.
4. Mande esse arquivo para o celular.
5. No app, toque em **Cookies** e escolha o `cookies.txt`.

Pronto, agora os Reels e posts que precisam de login vão baixar. O arquivo fica
guardado só dentro do app, na pasta privada dele.

> Use isso apenas na sua própria conta. É o mesmo princípio de estar logado no
> navegador.

---

## 6. Manutenção (muito importante)

As plataformas mudam com frequência e podem "quebrar" o download de um dia para
o outro. Quem conserta isso é o time do yt-dlp, várias vezes por semana.

Por isso existe o botão **Atualizar motor** no rodapé do app: ele baixa a versão
mais nova do yt-dlp direto do GitHub e substitui a que veio no APK.

**Se algum vídeo parar de baixar, toque primeiro em "Atualizar motor".**
É o que resolve na grande maioria dos casos — sem precisar reinstalar o app.

---

## 7. Limitações que você deve saber

- O download continua rodando **com o app minimizado**: aparece uma notificação
  com o progresso e o botão **Cancelar**. Você pode usar o celular e até apagar a
  tela que ele segue baixando. Só para se você **fechar o app pela lista de
  recentes** — no Android 14+ isso encerra o app e o download junto.
- Os downloads acontecem **um por vez** (fila), de propósito: cada download abre
  um processo de Python com o yt-dlp dentro, e rodar vários ao mesmo tempo falha
  por falta de memória num aparelho de 3 GB como o Galaxy A12. Se você mandar
  3 links, o segundo só começa quando o primeiro terminar.
- O **YouTube** mudou bastante em 2024/2025 e alguns vídeos podem pedir login.
  Nesse caso, importe os cookies do `youtube.com` pelo botão **Cookies**.
- Vídeos **privados**, com **restrição de idade**, de contas privadas ou com
  **DRM** (Netflix, Disney+, Globoplay...) não são suportados — é uma limitação
  do próprio yt-dlp, não do app.
- Post **carrossel** do Instagram baixa só o primeiro vídeo do post (o app usa
  `--no-playlist` para não baixar coisa demais sem você pedir).
- O APK fica em torno de **100 MB**, porque carrega dentro dele o Python e o
  FFmpeg. Isso é normal para esse tipo de app.

---

## 8. Problemas comuns

| Problema | Solução |
|---|---|
| "Não deu para salvar" / erro de armazenamento | Abra **Configurações → Apps → Baixa Vídeo → Permissões** e libere **Arquivos e mídia**. Em Android 10, ative também "Permitir gerenciar todos os arquivos" caso apareça. |
| "Requested format is not available" | Toque em **Atualizar motor**. |
| "Sign in to confirm you're not a bot" (YouTube) | Importe os cookies do YouTube pelo botão **Cookies**. |
| Login necessário no Instagram/Facebook | Importe os cookies, como no item 5. |
| Baixou só o som no TikTok | No TikTok, escolha **Melhor qualidade** (no modo MP3 o app prioriza a faixa de áudio do vídeo, e alguns vídeos têm só a música). |
| A notificação não mostra o progresso | Libere as notificações: **Configurações → Apps → Baixa Vídeo → Notificações**. O download funciona igual, só não mostra o andamento fora do app. |
| O download parou quando fechei o app nos recentes | Isso é regra do Android 14+: fechar pelas recentes mata o app. Evite; apenas minimize para a tela inicial. |
| O app trava por alguns segundos ao abrir | Normal na **primeira** abertura: ele está extraindo o Python e o FFmpeg. As próximas são rápidas. |
| O arquivo não aparece na Galeria | Reinicie o celular ou abra pela lista **Baixados** do app (ele já avisa a Galeria, mas alguns aparelhos demoram). |

---

## 9. Estrutura do projeto

```
BaixaVideo/
├─ .github/workflows/build-apk.yml   robô que compila o APK no GitHub
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle.properties
└─ app/
   ├─ build.gradle.kts
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ java/com/baixavideo/app/
      │  ├─ App.kt              inicialização do motor (yt-dlp + ffmpeg)
      │  ├─ Presets.kt          qualidades e o filtro "sem marca d'água"
      │  ├─ Downloads.kt        fila, progresso, arquivos e histórico
      │  ├─ DownloadService.kt  mantém o download vivo em segundo plano
      │  ├─ HistoryAdapter.kt   lista de downloads
      │  └─ MainActivity.kt     tela principal
      └─ res/                   telas, cores, tema e ícone
```

Detalhes técnicos que valem registrar:

- Biblioteca: `io.github.junkfood02.youtubedl-android:library:0.18.1` e
  `:ffmpeg:0.18.1` (Maven Central).
- `minSdk 26`, `targetSdk 34`, Kotlin 1.9.24, AGP 8.5.2, Gradle 8.7, JDK 17.
- Os downloads rodam em um `CoroutineScope` ligado ao processo, com um
  **foreground service** (`dataSync`) e um **wake lock** parcial segurando tudo
  em pé enquanto a fila está cheia. A saída fica em `Download/BaixaVideo` — um
  dos diretórios acessíveis mesmo com o armazenamento restrito do Android 11+.

---

## 10. Compilar no PC (alternativa ao GitHub)

Se preferir compilar localmente em vez de usar o GitHub Actions:

1. Instale o **Android Studio** (versão Ladybug ou mais nova).
2. Em **File → Open**, escolha a pasta `BaixaVideo`.
3. Espere o Gradle sincronizar (ele baixa tudo sozinho).
4. Menu **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

---

## 11. Aviso legal

Este app é uma ferramenta pessoal que usa software livre (yt-dlp). Baixe apenas
conteúdo que você tem o direito de baixar: seus próprios vídeos, material de
domínio público, ou conteúdo cujo autor permita o download.

Respeite direitos autorais e os termos de uso das plataformas. Não redistribua
o que você baixar sem autorização de quem criou.
