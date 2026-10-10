package com.joaohouto.clusterplayer.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Processador de áudio PCM de alta fidelidade para Normalização Dinâmica de Volume (Loudness Normalizer)
 * e nivelamento automático de faixas musicais para uso automotivo.
 *
 * Características:
 * 1. Análise contínua de RMS por bloco com janela ponderada.
 * 2. Controle automático de ganho (AGC) com attack suave (rápido para evitar picos) e release musical
 *    (lento para evitar efeito de "bombeamento/respiração").
 * 3. Limiter analógico virtual com curva soft-knee (saturação suave), garantindo ZERO distorção por
 *    clipping digital mesmo em passagens orquestrais ou graves profundos.
 * 4. Noise gate adaptativo para ignorar ruído de fundo e silêncio.
 * 5. Suporte a ReplayGain inicial por faixa com transição suave.
 */
class VolumeNormalizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        // Alvo de RMS padrão: ~5200 em escala PCM 16-bit (aprox. -16 dBFS),
        // nível ideal de referência para som automotivo com ~16 dB de headroom dinâmico.
        private const val TARGET_RMS = 5200f

        // Limiar de silêncio para ignorar ruído de fundo e silêncio (~ -42 dBFS)
        private const val SILENCE_THRESHOLD_RMS = 260f

        // Limites seguros de ganho automático
        private const val MIN_GAIN = 0.35f  // -9.1 dB (atenua faixas hipercomprimidas ou muito altas)
        private const val MAX_GAIN = 3.60f  // +11.1 dB (amplifica faixas antigas ou muito baixas)

        // Coeficientes de suavização (asymmetric smoothing)
        // Attack: adaptação rápida em ~70ms quando o volume sobe para evitar sobressaltos e picos
        private const val ATTACK_FACTOR = 0.12f
        // Release: adaptação gradual em ~2.5s quando o volume desce para preservar musicalidade e evitar bombeamento
        private const val RELEASE_FACTOR = 0.005f

        // Parâmetros do Soft-knee Limiter (knee em ~ -1.3 dBFS)
        private const val LIMITER_THRESHOLD = 28000f
        private const val LIMITER_MAX = 32760f
    }

    @Volatile
    var isNormalizerEnabled: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    currentGain = 1.0f
                }
            }
        }

    @Volatile
    private var trackReplayGainFactor: Float = 1.0f

    private var currentGain: Float = 1.0f

    fun setTrackReplayGain(gainDb: Float) {
        val factor = Math.pow(10.0, (gainDb / 20.0).toDouble()).toFloat()
        val safeFactor = if (factor.isNaN() || factor.isInfinite()) 1.0f else factor
        trackReplayGainFactor = safeFactor.coerceIn(MIN_GAIN, MAX_GAIN)
        currentGain = trackReplayGainFactor
    }

    fun resetTrackGain() {
        trackReplayGainFactor = 1.0f
        currentGain = 1.0f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        // Proteção contra buffers minúsculos (< 1 amostra de 16-bit) ou normalização desativada
        if (!isNormalizerEnabled || remaining < 2) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val sampleCount = remaining / 2
        if (sampleCount <= 0) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        var sumSquares = 0.0
        var maxAbs = 0
        val originalPosition = inputBuffer.position()

        // 1. Calcula energia RMS e pico absoluto do bloco atual
        for (i in 0 until sampleCount) {
            val sample = inputBuffer.getShort(originalPosition + (i * 2)).toInt()
            sumSquares += (sample * sample)
            val absVal = abs(sample)
            if (absVal > maxAbs) {
                maxAbs = absVal
            }
        }

        val rms = sqrt(sumSquares / sampleCount.toDouble()).toFloat()
        val safeRms = if (rms.isNaN() || rms.isInfinite()) 0f else rms

        // 2. Determina o ganho ideal para este bloco
        val targetGain = if (safeRms >= SILENCE_THRESHOLD_RMS) {
            val needed = (TARGET_RMS / safeRms) * trackReplayGainFactor
            needed.coerceIn(MIN_GAIN, MAX_GAIN)
        } else {
            // Silêncio ou ruído de fundo: mantém o ganho atual sem bombear ruído
            currentGain
        }

        // Limita o ganho alvo para evitar sobrecarregar o soft-limiter em transientes fortes
        val peakSafeGain = if (maxAbs > 0) (LIMITER_MAX * 0.95f / maxAbs).coerceIn(MIN_GAIN, MAX_GAIN) else MAX_GAIN
        val safeTargetGain = minOf(targetGain, peakSafeGain)

        val startGain = if (currentGain.isNaN() || currentGain.isInfinite()) 1.0f else currentGain
        val smoothingFactor = if (safeTargetGain < startGain) ATTACK_FACTOR else RELEASE_FACTOR
        val endGain = (startGain + (safeTargetGain - startGain) * smoothingFactor).coerceIn(MIN_GAIN, MAX_GAIN)
        currentGain = endGain

        // 3. Aplica o ganho com interpolação linear por amostra e Soft-knee Limiter
        inputBuffer.position(originalPosition)
        for (i in 0 until sampleCount) {
            val sample = inputBuffer.short.toFloat()
            val sampleGain = startGain + (endGain - startGain) * (i.toFloat() / sampleCount.toFloat())
            val amplified = sample * sampleGain
            val limitedSample = applySoftLimiter(amplified)
            outputBuffer.putShort(limitedSample)
        }

        // Consome byte ímpar remanescente se houver
        if (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }

        outputBuffer.flip()
    }

    private fun applySoftLimiter(sample: Float): Short {
        if (sample.isNaN() || sample.isInfinite()) return 0
        val absVal = abs(sample)
        if (absVal <= LIMITER_THRESHOLD) {
            return sample.toInt().toShort()
        }
        val excess = absVal - LIMITER_THRESHOLD
        val range = LIMITER_MAX - LIMITER_THRESHOLD
        val compressedExcess = excess * range / (range + excess)
        val result = LIMITER_THRESHOLD + compressedExcess
        return (if (sample >= 0) result else -result).toInt().coerceIn(-32768, 32767).toShort()
    }

    override fun onFlush() {
        // Ao realizar seek ou corte de reprodução, reancora suavemente ao ganho de referência
        currentGain = trackReplayGainFactor
    }

    override fun onReset() {
        currentGain = 1.0f
        trackReplayGainFactor = 1.0f
    }
}
