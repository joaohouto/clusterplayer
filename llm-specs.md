# Especificação Técnica: Cluster Player (Documentação Final do Produto)

Documento mestre de arquitetura, engenharia, design e implementação do **Cluster Player** — player de música local de alta performance, projetado sob medida para centrais multimídia automotivas Android.

---

## 1. Visão Geral do Produto

* **Propósito:** Player de áudio focado exclusivamente na reprodução de arquivos locais armazenados no armazenamento interno e em unidades removíveis USB (pendrives e HDs externos), garantindo máxima rapidez, ergonomia de direção e estabilidade.
* **Premissa Operacional:** Funcionamento **100% offline** (sem internet, sem telemetria, sem login ou dependência de serviços externos).
* **Paradigma de Organização por Pastas:** O aplicativo não impõe agrupamento complexo por tags ID3 de gênero ou álbum. As **pastas físicas** do sistema de arquivos funcionam diretamente como **Playlists**.
* **Estética Visual (Automotive Cluster UI):** Design inspirado em painéis e mostradores analógicos/digitais de carros esportivos e de luxo: fundo metálico acetinado escuro (`#0B0C0E`), botões táteis chanfrados com áreas de clique generosas (mínimo 60dp) e iluminação dinâmica de instrumentos configurável.
* **Boot Escuro Instantâneo (Zero Flash Branco):** Janela nativa com fundo metálico escuro em XML (`#0B0C0E`) desde o milissegundo zero, impedindo qualquer clarão branco na multimídia ao ligar a ignição do carro.
* **Abertura Inteligente:** Se não houver música carregada/tocando ao abrir o app, a interface abre diretamente na tela de **Pastas / Playlists**. Se uma música estiver em reprodução (ou recuperada pelo autoplay), abre diretamente na tela do **Player**.

---

## 2. Stack Tecnológica

| Componente | Tecnologia Escolhida | Papel no Sistema |
| :--- | :--- | :--- |
| **Linguagem** | Kotlin 2.0+ (JVM 17) | Concorrência estruturada via Corrotinas e Flow. |
| **Interface (UI)** | Jetpack Compose (BOM) + Material 3 | UI declarativa com isolamento fino de recomposições. |
| **Engine de Áudio** | **AndroidX Media3 (ExoPlayer 1.3.1 + MediaSessionService)** | Controle de foco de áudio, integração com teclas de volante e serviço em background. |
| **Efeitos de Áudio** | `android.media.audiofx.LoudnessEnhancer` | Reforço e nivelamento de áudio automotivo. |
| **Atenuador Master** | Software Volume Scaler (`player.volume`) | Ajuste fino de ganho (10% a 100%) para DACs e saídas amplificadas de centrais chinesas. |
| **Carregamento de Imagens** | Coil 2.6.0 (`ImageLoaderFactory`) | Decodificação customizada em `RGB_565`, cache em disco de 32MB e teto de 15% de RAM. |
| **Banco de Dados Local** | Room Database 2.6.1 (KSP) | Cache rápido do índice de pastas e faixas para abertura instantânea. |
| **Persistência de Ajustes** | Jetpack DataStore Preferences 1.1.1 | Persistência assíncrona de estado, autoplay, posição e preferências do usuário. |
| **Metadados e Capas** | `MediaMetadataRetriever` + `AudioArtExtractor` | Extração de ID3 e capas embutidas com cache estrito de 8MB em memória. |
| **Compilação e Otimização** | R8 Minification + ProGuard | Encolhimento de código e remoção de recursos não utilizados (APK de apenas 3.4 MB). |

---

## 3. Diretrizes de Design & Ergonomia Automotiva

### 3.1 Paleta de Cores e Materiais
* **Fundo Metálico Profundo (`DeepMetallicBackground`):** `#0B0C0E` (evita o preto puro OLED; replica o acabamento fosco de painéis de instrumentos).
* **Cartões e Superfícies (`SurfaceCard`):** Gradiente de `#181A1F` para `#121316` com contornos em `#262930`.
* **Frisos e Bordas Metálicas:** `#2E323A` a `#3A3F47`.
* **Acentos Dinâmicos de Iluminação (Cluster Accent Themes):**
  * **Needle Red (Padrão):** `#E61924` (Vermelho esportivo ponteiro de velocímetro).
  * **Electric Cyan:** `#00E5FF` (Azul ciano digital).
  * **Amber Sport:** `#FF9100` (Laranja clássico de mostradores esportivos).
  * **Acid Lime:** `#76FF03` (Verde esportivo de alto contraste).
  * **Racing Yellow:** `#FFEA00` (Amarelo automobilismo).
  * **Polar White:** `#FFFFFF` (Branco puro neutro).
* **Tipografia Primária:** `#FFFFFF` (Pesos SemiBold e Bold para legibilidade imediata em movimento).
* **Tipografia Secundária:** `#9A9DA6` (Artista, álbum e tempos decorridos).

### 3.2 Condução Segura e Hitboxes
* **Área de Toque Mínima:** Todos os botões de ação e navegação possuem dimensões mínimas de **60x60 dp** a **72x72 dp**.
* **Capa Quadrada Limpa:** Formato 1:1, cantos levemente arredondados (`8dp`), sem aros decorativos que cortam a arte.
* **Barra de Progresso com Alvo Expandido:** Trilho interativo com área tátil ampliada (36dp de altura útil) para ajuste de posição sem risco de distração na condução.

---

## 4. Telas e Funcionalidades

### 4.1 Tela 1: Navegador de Pastas (HomeScreen)
1. **Cabeçalho:**
   * Título *"Pastas e Playlists"* em caixa alta com estilo de painel.
   * Indicador visual de escaneamento em progresso (quando pendrive for inserido ou forçado reescaneamento).
   * Botão metálico de **Atualizar** (força varredura física completa do armazenamento).
   * Botão metálico de **Configurações** (abre painel modal).
2. **Grade de Pastas (Grid Adaptativo):**
   * Colunas adaptativas com largura mínima de 320dp (excelente em telas widescreen 1024x600, 1280x720 ou ultrawide).
   * Cada cartão exibe: ícone de pasta metálica, nome da pasta, total de faixas e botão direto de reprodução.
3. **Barra de Reprodução Completa (Bottom Playback Bar):**
   * Exibida na base caso haja música em reprodução ou carregada.
   * Exibe capa (56x56), título, artista e **todos os controles essenciais**: Anterior, Play/Pause, Próximo, Aleatório, Repetir e toque no card para abrir o Player.
   * **Desempenho Zero-Lag:** Conectada a um fluxo de dados desacoplado do relógio da música; **não executa nenhuma recomposição por segundo** durante a execução da faixa.

### 4.2 Tela 2: Player de Reprodução (PlayerScreen)
1. **Barra Superior:**
   * Logo/Marca *"CLUSTER PLAYER"*.
   * Botão **"Músicas da Pasta"**: abre modal com a lista completa de faixas da pasta atual para seleção rápida.
   * Botão **"Pastas"**: retorna à tela de seleção de pastas.
   * Botão **"Configurações"**: abre o diálogo de ajustes.
2. **Área Central (Layout Horizontal Widescreen):**
   * **Esquerda:** Capa de álbum quadrada adaptativa (até 200dp de altura máxima, nunca estoura a tela).
   * **Direita:**
     * **Letras Sincronizadas (.LRC):** Painel com altura fixa de 32dp acima do título. Exibe a frase exata correspondente ao milissegundo atual com ícone de equalizador luminoso no acento do tema. Altura fixa garante que título, artista e barra nunca se movam.
     * **Título da Faixa:** Tipografia em 30sp Bold de alto contraste.
     * **Artista e Álbum:** 20sp SemiBold em tom secundário.
     * **Barra de Progresso Isolada:** Controlada por composable dedicado (`TrackProgressSection`), evitando recomposição do restante da tela a cada tick de 250ms.
3. **Barra Inferior de Controles:**
   * Botões de toque largo: `Aleatório`, `Anterior`, `Play / Pause` (em destaque com estilo de acento), `Próximo`, `Repetir` (Off / Pasta / Uma).
4. **Gestos Rápidos no Volante/Tela:**
   * **Deslizar horizontalmente (Swipe):** Avança ou retrocede a faixa.
   * **Toque duplo na capa:** Play / Pause.

### 4.3 Diálogo de Configurações (SettingsDialog)
Diálogo simplificado, escuro e neutro, com as seguintes opções:
1. **Tema de Iluminação:** Seletor visual dos 6 esquemas de cores metálicas automotivas.
2. **Atenuador de Áudio:** Slider de 10% a 100% para calibrar o volume de saída do app e evitar saturação em multimídias sensíveis.
3. **Reforço de Graves e Volume (Loudness Boost):** Toggle ativando `LoudnessEnhancer`.
4. **Transição Suave (Crossfade):** Slider de 0 a 10s (desativado por padrão para máxima economia de CPU).
5. **Tocar ao Iniciar (Autoplay):** Alternador para iniciar automaticamente ao abrir o app.
6. **Manter Tela Ligada:** Evita suspensão da tela enquanto o player estiver em primeiro plano.
7. **Exibir Letras (.LRC):** Habilita ou desabilita o painel de letras sincronizadas.
8. **Cartão de Informações / Link do App:** Card neutro com versão e link direto para a página do projeto no GitHub Pages.

---

## 5. Engenharia de Performance para Hardware Modesto (Cortex-A7 / 1GB RAM)

### 5.1 Eliminação de Recomposições Inúteis no Compose
* **Isolamento do Ticker de 250ms:** O `PlayerController` emite o tempo decorrido exclusivamente para um `StateFlow<Long>` dedicado (`currentPosition`). O estado geral da interface (`PlaybackUiState`) só emite novas instâncias quando faixa, estado de play/pause ou linha de letra mudam.
* **Isolamento da HomeScreen:** A tela de pastas observa `bottomBarState` filtrado com `distinctUntilChanged()`. O grid de pastas executa **0 recomposições por segundo** enquanto uma música toca.
* **Otimização de Botões:** Em [`MetallicButton.kt`](file:///home/joao/AndroidStudioProjects/ClusterPlayer/app/src/main/java/com/joaohouto/clusterplayer/ui/components/MetallicButton.kt), o formato `ButtonShape = RoundedCornerShape(12.dp)` é estático e instanciado uma única vez; gradientes e bordas são armazenados com `remember(...)`.
* **Substituição de `String.format`:** Na barra de progresso, a formatação de minutos e segundos foi substituída por operações aritméticas puras de divisão inteira, eliminando alocações contínuas de strings e tabelas de locale a cada fração de segundo.

### 5.2 Otimização de Memória e Prevenção de OOM
* **Classe de Aplicação Customizada ([`ClusterPlayerApplication.kt`](file:///home/joao/AndroidStudioProjects/ClusterPlayer/app/src/main/java/com/joaohouto/clusterplayer/ClusterPlayerApplication.kt)):**
  * `ImageLoaderFactory` do Coil configurado com **`Bitmap.Config.RGB_565`** (reduz pela metade o uso de memória por pixel, caindo de 4 para 2 bytes).
  * Limite estrito de cache de RAM de imagens em 15% da memória física do aparelho.
  * Cache em disco fixado em 32 MB.
* **`android:largeHeap="true"`:** Habilitado no manifesto para fornecer margem segura contra OOMs em aparelhos com 1GB/2GB de RAM.
* **Cache em Bytes no [`AudioArtExtractor.kt`](file:///home/joao/AndroidStudioProjects/ClusterPlayer/app/src/main/java/com/joaohouto/clusterplayer/ui/components/AudioArtExtractor.kt):** Limite de **8 MB** baseado no tamanho real dos bytes das imagens ID3, e não em quantidade arbitrária de arquivos.
* **Downsampling:** Capas requisitadas no tamanho máximo de **256x256** (~131 KB em RAM por imagem).

### 5.3 Otimização de Armazenamento eMMC Flash e CPU
* **Intervalo de Posição:** A gravação contínua da posição da música no DataStore foi dilatada de 3s para **10 segundos** durante a reprodução, preservando a vida útil do chip de memória flash eMMC da central.
* **Ticker de Fade Condicional:** O loop de transição gradual de áudio em [`PlaybackService.kt`](file:///home/joao/AndroidStudioProjects/ClusterPlayer/app/src/main/java/com/joaohouto/clusterplayer/player/PlaybackService.kt) só inicializa quando `crossfadeSeconds > 0`. Desativado por padrão, resulta em zero wakeups coroutine adicionais no Dispatcher principal.
* **Eliminação de Pastas Duplicadas:**
  * Uso de `File.canonicalPath` para resolver montagens redundantes (`/sdcard`, `/mnt/sdcard`, `/storage/self/primary`).
  * Descarte de pastas sob `/mnt/media_rw` quando o pendrive correspondente já está acessível via `/storage/`.
  * Deduplicação por nome e contagem de músicas.
* **Reaproveitamento de `MediaMetadataRetriever`:** Uma única instância nativa é reutilizada durante todo o escaneamento de um pendrive, evitando a criação e destruição repetitiva de instâncias nativas C++.

---

## 6. Arquitetura do Código-Fonte

```text
com.joaohouto.clusterplayer/
├── ClusterPlayerApplication.kt          // Coil ImageLoaderFactory (RGB_565, 15% RAM)
├── MainActivity.kt                      // Inicialização, permissões, rota dinâmica e tela
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt               // Room Database com migrações
│   │   ├── PreferencesDataStore.kt      // DataStore Preferences para todos os ajustes
│   │   ├── dao/
│   │   │   ├── FolderDao.kt
│   │   │   └── TrackDao.kt
│   │   └── entity/
│   │       ├── FolderEntity.kt
│   │       └── TrackEntity.kt
│   ├── model/
│   │   ├── Folder.kt                    // Modelo de domínio de pasta
│   │   └── Track.kt                     // Modelo de domínio de faixa
│   ├── repository/
│   │   └── MusicRepository.kt           // Sincronização entre Scanner e Room
│   └── scanner/
│       └── StorageScanner.kt            // Varredura física canônica de USB e disco
├── player/
│   ├── PlaybackService.kt               // MediaSessionService do Media3
│   ├── PlayerController.kt              // Ponte entre Service e UI com StateFlows
│   ├── AudioEffectsManager.kt           // Gerenciamento de LoudnessEnhancer
│   └── AutoMediaButtonReceiver.kt       // Suporte a botões de volante
├── receiver/
│   └── UsbMountReceiver.kt              // BroadcastReceiver de montagem/desmontagem USB
└── ui/
    ├── components/
    │   ├── AudioArtExtractor.kt         // Extração ID3 com LRU Cache de 8MB
    │   ├── MetallicButton.kt            // Botões chanfrados com cache de shapes
    │   ├── ProgressBarSlider.kt         // Barra tátil com formatação aritmética
    │   └── SquareAlbumArt.kt            // Capa 1:1 downsampled (256x256 RGB_565)
    ├── home/
    │   ├── HomeScreen.kt                // Grid de pastas e bottom bar desacoplada
    │   └── HomeViewModel.kt
    ├── player/
    │   ├── PlayerScreen.kt              // Player mestre com progresso isolado
    │   └── PlayerViewModel.kt
    ├── settings/
    │   └── SettingsDialog.kt            // Modal simplificado de configurações
    └── theme/
        ├── AccentTheme.kt               // Temas de iluminação (Needle Red, Cyan, etc.)
        ├── Color.kt                     // Paleta metálica escura
        ├── Theme.kt                     // ClusterPlayerTheme
        └── Type.kt
```

---

## 7. Minificação R8 e Binário de Distribuição

O aplicativo foi configurado com regras estritas de ProGuard para permitir minificação R8 completa sem comprometer o AndroidX Media3, Room ou Coil:

```kotlin
// app/build.gradle.kts
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        signingConfig = signingConfigs.getByName("release")
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

### Resultados de Compilação:
* **Tamanho original do APK:** ~14.3 MB
* **Tamanho final minificado (v1.1.2):** **3.4 MB** (**Redução de 76.2%**)
* **Tempo de inicialização (Cold Start):** Instantâneo (< 200ms em hardware modesto), com tela inicial 100% escura.