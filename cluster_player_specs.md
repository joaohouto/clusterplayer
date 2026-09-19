# Especificação Técnica: Cluster Player

Documento mestre de arquitetura, design e implementação de player de música local para centrais multimídia automotivas Android.

---

## 1. Visão Geral do Produto

* **Propósito:** Player de áudio estritamente focado em execução de arquivos locais (armazenamento interno e unidades USB/pendrives) para centrais multimídia automotivas.
* **Paradigma de Organização:** O aplicativo não utiliza agrupamento por tags de gênero/álbum; as **pastas físicas** do sistema de arquivos atuam diretamente como **Playlists**.
* **Estética:** *Automotive Cluster UI* — fundo escuro metálico acetinado, contrastes sóbrios, botões de toque amplo e acento vermelho esportivo inspirado em instrumentos de painéis de luxo.

---

## 2. Stack Tecnológica Obrigatória

| Componente | Tecnologia Escolhida | Justificativa Técnica |
| :--- | :--- | :--- |
| **Linguagem** | Kotlin | Padrão Android, concisão e suporte a corrotinas. |
| **Interface (UI)** | Jetpack Compose | Layouts declarativos responsivos para telas widescreen/landscape. |
| **Engine de Áudio** | **AndroidX Media3 (ExoPlayer + MediaSessionService)** | Padrão oficial; lida com ciclo de vida em background, teclas de volante e foco de áudio nativo. |
| **Equalização / Nivelamento** | `android.media.audiofx.LoudnessEnhancer` + `Equalizer` | Normalização de volume de faixas e ajustes de graves/médios/agudos. |
| **Carregamento de Imagens** | Coil | Leve, integrado ao Compose, com decodificação de tags ID3 e suporte a downsampling estrito. |
| **Banco de Dados Local** | Room Database | Indexação rápida de pastas e faixas, evitando leituras lentas de disco no boot. |
| **Persistência de Estado** | Jetpack DataStore (Preferences) | Salva última faixa, posição em milissegundos e estado de reprodução. |
| **Metadados de Arquivo** | `MediaMetadataRetriever` / AndroidX Media3 | Extração de ID3 (Título, Artista, Álbum, Capa embutida). |

---

## 3. Diretrizes Visuais & Design System

### 3.1 Paleta de Cores
* **Deep Metallic Background:** `#0B0C0E` a `#121316` (evita pretos absolutos OLED sem profundidade; simula o interior de um mostrador analógico).
* **Surface & Card Background:** `#181A1E` com bordas sutis em `#262930`.
* **Metálico Intermediário / Bordas:** `#2E323A` a `#3A3F47`.
* **Acento (Needle/Accent Red):** `#E61924` (destaque ativo, preenchimento da barra de progresso e botão Play).
* **Tipografia Primária:** `#FFFFFF` (Texto com peso *Medium* ou *Bold* para leitura à distância).
* **Tipografia Secundária:** `#9A9DA6` (Artista, informações secundárias e tempos).
* **Fonte:** Fonte padrão do sistema Android (sem necessidade de carregar fontes externas pesadas).

### 3.2 Ergonomia para Direção
* **Hitbox mínima:** Todos os botões de ação devem ter área de clique de no mínimo **60x60 dp**.
* **Sem anéis ou bordas decorativas na capa:** A arte da música é um quadrado simples com cantos sutilmente arredondados (`8dp`).
* **Contraste elevado:** Evitar cinzas de baixo contraste que fiquem ilegíveis sob luz solar direta.

---

## 4. Telas e Interações

### 4.1 Tela 1: Início (Navegador de Pastas / Playlists)
1. **Estrutura:** Grid ou Lista horizontal/vertical otimizada para formato landscape (16:9 ou ultrawide).
2. **Item da Pasta:**
   * Nome da pasta (Ex.: *"Rock Clássico 80s"*, *"Eletrônica USB"*).
   * Contador de músicas (Ex.: `48 faixas`).
   * Botão direto **"Reproduzir Todas"** que limpa a fila atual, enfileira os itens da pasta e inicia a reprodução imediatamente.
3. **Barra de Reprodução Rápida (Mini-player fixo):**
   * Exibida na base ou topo caso haja uma faixa tocando, permitindo retornar ao player com 1 toque.

### 4.2 Tela 2: Player (Fiel ao Wireframe)
1. **Lado Esquerdo:**
   * Capa da música quadrada (proporção 1:1, limpa, sem aros metálicos circulares, raio de 8dp).
   * Fallback com ícone de nota musical em tom metálico caso o arquivo não possua capa embutida.
2. **Lado Direito Superior:**
   * Botão de navegação: `Playlists / Pastas` (leva de volta à Tela de Início).
3. **Lado Direito Central (Metadados & Progresso):**
   * Nome da faixa (destaque, tamanho 22–24sp, texto em 1 ou 2 linhas com elipse).
   * Artista / Álbum (16sp, cor `#9A9DA6`).
   * **Barra de Progresso:**
     * Trilho: cinza escuro metálico (`#262930`).
     * Preenchimento: vermelho esportivo (`#E61924`).
     * Rótulos de tempo: decorrido à esquerda (`01:45`) e restante/total à direita (`-02:15` ou `04:00`).
4. **Base (Controles de Reprodução):**
   * `Shuffle` (Aleatório: Ativado/Desativado com indicador vermelho).
   * `Anterior (<)` (Muda para a faixa anterior ou reinicia a atual se > 3s).
   * `Play / Pause ( || / ▶ )` (Botão centralizado de destaque).
   * `Próximo (>)` (Avança faixa).
   * `Repetir` (Alterna entre: Desativado, Repetir Pasta/Todas, Repetir Uma).
5. **Gestos Rápidos:**
   * Arrastar horizontalmente sobre o player: Avançar / Retroceder faixa.
   * Toque duplo sobre a capa do álbum: Play / Pause.

---

## 5. Regras de Negócio e Comportamento

### 5.1 Autoplay e Retomada Perfeita
* Ao abrir o aplicativo, o player deve:
  1. Recuperar via **DataStore**: `lastTrackUri`, `lastFolderUri`, `lastPositionMs`, `shuffleMode`, `repeatMode`.
  2. Carregar a fila referente à última pasta reproduzida.
  3. Realizar o `seekTo(lastPositionMs)` e disparar `play()` automaticamente sem exigir interação manual do condutor.

### 5.2 Nivelamento de Volume & Áudio
* Vincular um `LoudnessEnhancer` ao `audioSessionId` do player Media3.
* Configurar ganho padrão de equalização de loudness (ex.: `targetGain` moderado para evitar distorção harmônica).
* Integrar `AudioAttributes` configurado com:
  * `USAGE_MEDIA`
  * `CONTENT_TYPE_MUSIC`
  * `handleAudioFocus = true` (gerencia automaticamente avisos do GPS/Waze, chamadas viva-voz e marcha ré).

### 5.3 Arquivos de Letras (.LRC)
* Quando uma faixa `musica.mp3` for carregada:
  1. Verificar no mesmo diretório a presença de `musica.lrc`.
  2. Se existir, realizar o parse assíncrono das linhas com timestamp `[mm:ss.xx]`.
  3. (Opcional/Fase 2) Exibir a linha correspondente ao segundo atual em um painel discreto.

---

## 6. Riscos Automotivos e Tratamento de Gargalos

| Problema Conhecido | Risco | Solução de Engenharia |
| :--- | :--- | :--- |
| **Corte de Corrente (Desligamento Abrupto)** | A central não passa pelos ciclos normais (`onDestroy`). O estado não é salvo. | Salvar o `lastPositionMs` no DataStore a cada **3 segundos** durante a reprodução via corrotina em background. |
| **Atraso na montagem da porta USB** | O app inicia no boot antes do pendrive ser montado pelo Android; o autoplay falha. | Registrar `BroadcastReceiver` ouvindo `Intent.ACTION_MEDIA_MOUNTED`. Se a faixa salva for de caminho externo, esperar a montagem antes de alertar erro. |
| **Queda de Tensão na Partida do Motor** | O motor de arranque faz a USB reiniciar brevemente. | Implementar estratégia de retentativa automática (retry com exponential backoff) de até 3 tentativas no carregamento do arquivo. |
| **Esgotamento de Memória (OOM)** | Capas embutidas em FLACs de alta fidelidade podem ter mais de 3000x3000px. | Configurar o Coil com `.size(512, 512)` estrito e downsampling habilitado. |
| **Congelamento de UI ao ler muitas faixas** | Escanear 5.000 músicas no storage bloqueia a Main Thread (ANR). | Varredura em thread dedicada (`Dispatchers.IO`), inserindo lotes (batches) no Room Database; a UI apenas consome `Flow<List<Track>>`. |

---

## 7. Arquitetura do Código (Android Studio)

```text
com.automotive.player/
├── data/
│   ├── local/
│   │   ├── dao/
│   │   │   ├── FolderDao.kt
│   │   │   └── TrackDao.kt
│   │   ├── entity/
│   │   │   ├── FolderEntity.kt
│   │   │   └── TrackEntity.kt
│   │   ├── AppDatabase.kt
│   │   └── PreferencesDataStore.kt      // Salva autoplay, tempos e estados
│   ├── model/
│   │   ├── Folder.kt
│   │   └── Track.kt
│   ├── repository/
│   │   └── MusicRepository.kt           // Media scanning + Room sync
│   └── scanner/
│       └── StorageScanner.kt            // Leitura da raiz e pontos /storage
├── player/
│   ├── PlaybackService.kt               // AndroidX MediaSessionService
│   ├── AudioEffectsManager.kt           // LoudnessEnhancer & Equalizer
│   └── AutoMediaButtonReceiver.kt       // Suporte a teclas físicas de volante
├── ui/
│   ├── components/
│   │   ├── MetallicButton.kt            // Botão com toque visual automotivo
│   │   ├── ProgressBarSlider.kt         // Barra de progresso vermelha
│   │   └── SquareAlbumArt.kt            // Capa quadrada limpa com cache
│   ├── home/
│   │   ├── HomeScreen.kt                // Pastas / Playlists
│   │   └── HomeViewModel.kt
│   ├── player/
│   │   ├── PlayerScreen.kt              // Tela mestre do wireframe
│   │   └── PlayerViewModel.kt
│   └── theme/
│       ├── Color.kt                     // Cores escuras metálicas e vermelho
│       └── Theme.kt
└── MainActivity.kt
```

---

## 8. Dependências Base (`build.gradle.kts`)

```kotlin
dependencies {
    // AndroidX Media3 (Áudio e MediaSession)
    val media3Version = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    // Jetpack Compose & Material 3
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Persistência: Room & DataStore
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Imagens: Coil
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Ciclo de Vida & Corrotinas
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

---

## 9. Instruções de Implementação para o Agente (Prompt de Execução)

1. **Configuração Inicial:** Inicializar projeto Android targeting SDK 34, Min SDK 24 (compatível com Android 7.0+ encontrado na maioria das multimídias), habilitando Jetpack Compose.
2. **Serviço de Áudio:** Construir primeiro o `PlaybackService` estendendo `MediaSessionService` com Media3, tratando `AudioAttributes` e `Audio Focus` nativo.
3. **Mecanismo de Scanner:** Montar a leitura recursiva de pastas em `Environment.getExternalStorageDirectory()` e diretórios em `/storage/`, salvando apenas caminhos de pastas que contenham extensões suportadas (`.mp3`, `.flac`, `.wav`, `.m4a`, `.aac`).
4. **Resiliência do Autoplay:** Configurar corrotina no `PlaybackService` para atualizar `lastPositionMs` no DataStore a cada 3 segundos e recuperar este valor no `onCreate` do serviço.
5. **Composição da UI:** Criar a interface em Compose obedecendo fielmente ao layout do wireframe: capa quadrada à esquerda, dados e barra vermelha à direita, botões de toque largo (mínimo 60dp) na base.