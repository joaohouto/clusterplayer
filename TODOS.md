# TODOs & Padronização: Cluster Player

Este documento lista melhorias de harmonização arquitetural e técnica com o ecossistema **Cluster** (Cluster Launcher e Cluster Radio).

---

## 1. Aprimoramento de Teclas de Volante (SWC no Foreground) [CONCLUÍDO]

* **Status:** Implementado com sucesso em `MainActivity.kt` via interceptação de `dispatchKeyEvent(event: KeyEvent)`.
* **Teclas suportadas:**
  - `KeyEvent.KEYCODE_MEDIA_NEXT` / `KeyEvent.KEYCODE_CHANNEL_UP` (Avançar faixa)
  - `KeyEvent.KEYCODE_MEDIA_PREVIOUS` / `KeyEvent.KEYCODE_CHANNEL_DOWN` (Voltar faixa)
  - `KeyEvent.KEYCODE_MEDIA_PLAY` / `KeyEvent.KEYCODE_MEDIA_PAUSE` / `KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE` / `KeyEvent.KEYCODE_HEADSETHOOK` (Play/Pause)
* **Compatibilidade veicular:** Perfeita sincronia com centrais Android chinesas (FYT/Syu, Topway, Allwinner, Rockchip, Microntek) operando via MCU/CAN-Bus e botões físicos direct-key.

---

## 2. Harmonização de Design e Ícones

* O Cluster Player é a referência de design para o Cluster Radio e Cluster Launcher:
  - Fundo `#0B0C0E` com zero flash branco.
  - Botões táteis chanfrados `MetallicButton` com área mínima de toque de 60dp.
  - Ícone adaptativo com moldura circular usinada e anel esportivo *Needle Red* (`#E61924`).
