# Cluster Player 🎵

> **Reprodutor de música offline de alta performance com navegação por pastas, estética de cockpit esportivo e baixa latência para centrais multimídia automotivas Android.**

Parte do ecossistema **Cluster** (ao lado de [Cluster Launcher](https://joaohouto.github.io/clusterlauncher) e [Cluster Radio](https://github.com/joaohouto/clusterradio)). Acesse o portal oficial em [joaohouto.github.io/clusterlauncher](https://joaohouto.github.io/clusterlauncher).

---

## 🏎️ Destaques e Ergonomia de Cockpit

* **Navegação Física por Pastas e Pendrives USB:** Varredura física direta e robusta de dispositivos de armazenamento removíveis (USB, cartões SD) e memória interna, compatível com centrais multimídia chinesas (FYT/Syu, Topway, Allwinner, Rockchip, Microntek) e montagens customizadas.
* **Estética Automotiva Deep Metallic (`#0B0C0E`):** Acabamento escuro acetinado, botões chanfrados táteis de alta precisão (`MetallicButton`) e iluminação de instrumentos configurável (*Needle Red*, *M-Sport Blue*, *Racing Yellow*, *Green Hell*, *Sunset Orange*, *Electric Cyan*, *Pure Silver*).
* **Ergonomia e Condução Segura:** Hitbox generosa (mínimo de 60x60 dp) em todos os botões de ação e barra de controle inferior ampliada para 84dp, garantindo acionamento seguro e sem desvio de atenção durante a direção.
* **Suporte Completo a Letras Sincronizadas (.LRC):** Parser de letras offline de alto desempenho com sincronização milimétrica com a reprodução da faixa musical.
* **Áudio Avançado e Transição Suave (Crossfade):** Transições progressivas configuráveis entre faixas (0 a 10s), Loudness Enhancer calibrado para acústica automotiva e ajuste fino de ganho master.
* **Integração Veicular e Controles de Volante (SWC):** Interceptação direta no primeiro plano (`dispatchKeyEvent`) para botões de volante, teclas de canal e knobs rotativos, além de suporte a `AutoMediaButtonReceiver` para controle contínuo em segundo plano.
* **Gestão Inteligente de Foco e Autoplay:** Atenuação suave (*audio ducking*) automática ao receber instruções de navegadores GPS (Google Maps, Waze), restauração de reprodução na inicialização (*Autoplay on Start*) e gravação persistente de posição exata da última faixa ouvida.
* **Inicialização Escura Instantânea (Zero Flash Branco):** Janela nativa com fundo escuro pré-carregado no XML (`#0B0C0E`), eliminando clarões brancos ao ligar a ignição do veículo.

---

## 📦 Download do APK

Baixe a versão otimizada mais recente na aba de [Releases](https://github.com/joaohouto/clusterplayer/releases/latest) ou conheça a suíte completa no site oficial em [joaohouto.github.io/clusterlauncher](https://joaohouto.github.io/clusterlauncher).

---

## 🛠️ Tecnologias Utilizadas

* **UI:** Jetpack Compose com Material 3 e Custom Automotive Shaders/Gradients
* **Áudio & MediaSession:** AndroidX Media3 (1.3.1) & ExoPlayer
* **Banco de Dados Local:** Room com SQLite (cache canônico e indexação rápida de arquivos)
* **Persistência:** Jetpack DataStore Preferences
* **Linguagem & Tooling:** Kotlin 2.2 / Gradle 9 / Minificação R8 ProGuard
