package com.joaohouto.clusterplayer.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Processador de áudio PCM (16-bit e Float) para aplicação de ganho estático por faixa
 * segundo o padrão EBU R128 / ITU-R BS.1770-4 e ReplayGain, com rampa suave de transição
 * e limitador de pico soft-knee.
 *
 * Arquitetura de fonte única de verdade (Zero Pumping / Zero Ducking):
 * - Não realiza medição dinâmica bloco-a-bloco em tempo real (que causava variação de volume
 *   entre estrofes e refrões/graves).
 * - Aplica exclusivamente o ganho estático da faixa (vindo de tags ReplayGain/R128 ou do cache
 *   persistente `track_loudness` analisado em background pelo `EbuR128Scanner`).
 * - Para faixas ainda sem cache, inicia em ganho neutro (0 dB / 1.0x) e aplica uma rampa suave
 *   de 400ms assim que o scanner em segundo plano conclui a medição.
 * - Nas fronteiras entre faixas (gapless), aplica uma micro-rampa de 15ms para evitar degraus audíveis.
 */
class VolumeNormalizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        // Limites de ganho linear (~ -9.0 dB a +9.0 dB)
        private const val MIN_GAIN = 0.3548134f
        private const val MAX_GAIN = 2.818383f

        // Parâmetros do Soft-Knee Peak Limiter em escala normalizada [-1.0, +1.0]
        // Joelho suave em -1.2 dBFS (0.87096) e teto absoluto em -0.1 dBFS (0.98855)
        private const val LIMITER_THRESHOLD_NORM = 0.8709636f
        private const val LIMITER_CEILING_NORM = 0.9885531f
        private const val LIMITER_HEADROOM_NORM = LIMITER_CEILING_NORM - LIMITER_THRESHOLD_NORM

        const val BOUNDARY_RAMP_MS = 15
        const val ASYNC_SCAN_RAMP_MS = 400
    }

    @Volatile
    var isNormalizerEnabled: Boolean = true

    @Volatile
    private var isTagGainLocked: Boolean = false

    @Volatile
    private var targetGainLinear: Float = 1.0f

    @Volatile
    private var pendingRampDurationMs: Int = BOUNDARY_RAMP_MS

    @Volatile
    private var rampNeedsUpdate: Boolean = false

    // Estado interno da rampa (acessado na thread de áudio)
    private var currentGainLinear: Float = 1.0f
    private var activeTargetGainLinear: Float = 1.0f
    private var gainStepPerFrame: Float = 0f
    private var remainingRampFrames: Int = 0

    /**
     * Define o ganho estático da faixa em dB.
     *
     * @param gainDb Ganho em dB (ex: calculado via EBU R128 ou lido de tag ReplayGain/R128).
     * @param rampDurationMs Duração da rampa de transição (15ms na troca de faixa, 400ms se assíncrono).
     * @param fromTag Se verdadeiro, bloqueia sobrescrita pelo scanner da mesma faixa.
     */
    fun setTrackGainDb(
        gainDb: Float,
        rampDurationMs: Int = BOUNDARY_RAMP_MS,
        fromTag: Boolean = false
    ) {
        if (!fromTag && isTagGainLocked) {
            return
        }
        if (fromTag) {
            isTagGainLocked = true
        }
        val linear = if (gainDb.isFinite()) {
            10.0.pow(gainDb.toDouble() / 20.0).toFloat()
        } else {
            1.0f
        }
        targetGainLinear = linear.coerceIn(MIN_GAIN, MAX_GAIN)
        pendingRampDurationMs = rampDurationMs.coerceAtLeast(0)
        rampNeedsUpdate = true
    }

    /**
     * Compatibilidade para aplicação direta de tag ReplayGain/R128.
     */
    fun setTrackReplayGain(gainDb: Float, rampDurationMs: Int = BOUNDARY_RAMP_MS) {
        setTrackGainDb(gainDb = gainDb, rampDurationMs = rampDurationMs, fromTag = true)
    }

    /**
     * Reseta o ganho para neutro (0 dB / 1.0x) no início de uma nova faixa sem cache prévio,
     * usando micro-rampa de fronteira (15ms) para evitar clique em transição gapless.
     */
    fun resetTrackGain(rampDurationMs: Int = BOUNDARY_RAMP_MS) {
        isTagGainLocked = false
        targetGainLinear = 1.0f
        pendingRampDurationMs = rampDurationMs.coerceAtLeast(0)
        rampNeedsUpdate = true
    }

    fun getCurrentTargetGainDb(): Float {
        return (20.0 * kotlin.math.log10(targetGainLinear.toDouble())).toFloat()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    private fun syncRampStateIfNeeded() {
        if (!rampNeedsUpdate) return
        rampNeedsUpdate = false

        val newTarget = targetGainLinear
        activeTargetGainLinear = newTarget

        val sr = if (inputAudioFormat.sampleRate > 0) inputAudioFormat.sampleRate else 44100
        val rampMs = pendingRampDurationMs
        val totalFrames = (sr.toLong() * rampMs.toLong() / 1000L).toInt()

        if (totalFrames <= 1 || abs(newTarget - currentGainLinear) < 1e-5f) {
            currentGainLinear = newTarget
            remainingRampFrames = 0
            gainStepPerFrame = 0f
        } else {
            remainingRampFrames = totalFrames
            gainStepPerFrame = (newTarget - currentGainLinear) / totalFrames.toFloat()
        }
    }

    private fun nextFrameGain(): Float {
        if (remainingRampFrames > 0) {
            currentGainLinear += gainStepPerFrame
            remainingRampFrames--
            if (remainingRampFrames == 0) {
                currentGainLinear = activeTargetGainLinear
            }
        } else {
            currentGainLinear = activeTargetGainLinear
        }
        return currentGainLinear
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        if (!isNormalizerEnabled) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        syncRampStateIfNeeded()

        val channels = max(1, inputAudioFormat.channelCount)
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> processPcm16(inputBuffer, outputBuffer, channels)
            C.ENCODING_PCM_FLOAT -> processPcmFloat(inputBuffer, outputBuffer, channels)
            else -> outputBuffer.put(inputBuffer)
        }

        outputBuffer.flip()
    }

    private fun processPcm16(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, channels: Int) {
        val bytesPerFrame = channels * 2
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        val invScale = 1f / 32768f

        for (f in 0 until frameCount) {
            val frameGain = nextFrameGain()
            for (ch in 0 until channels) {
                val rawShort = inputBuffer.short
                val normalized = rawShort.toFloat() * invScale
                val amplified = normalized * frameGain
                val limited = applyNormalizedSoftLimiter(amplified)
                val outInt = (limited * 32767f).toInt().coerceIn(-32768, 32767)
                outputBuffer.putShort(outInt.toShort())
            }
        }

        while (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }
    }

    private fun processPcmFloat(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, channels: Int) {
        val bytesPerFrame = channels * 4
        val frameCount = inputBuffer.remaining() / bytesPerFrame

        for (f in 0 until frameCount) {
            val frameGain = nextFrameGain()
            for (ch in 0 until channels) {
                val sample = inputBuffer.float
                if (!sample.isFinite()) {
                    outputBuffer.putFloat(0f)
                } else {
                    val amplified = sample * frameGain
                    val limited = applyNormalizedSoftLimiter(amplified)
                    outputBuffer.putFloat(limited)
                }
            }
        }

        while (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }
    }

    /**
     * Limitador soft-knee com curva hiperbólica (tanh) acima de -1.2 dBFS e teto em -0.1 dBFS.
     * Transparente abaixo do limiar (linear 1:1) e livre de clipping acima dele.
     */
    internal fun applyNormalizedSoftLimiter(sample: Float): Float {
        if (!sample.isFinite()) return 0f
        val absVal = abs(sample)
        if (absVal <= LIMITER_THRESHOLD_NORM) {
            return sample
        }
        val excess = (absVal - LIMITER_THRESHOLD_NORM) / LIMITER_HEADROOM_NORM
        val compressed = LIMITER_THRESHOLD_NORM + LIMITER_HEADROOM_NORM * tanh(excess.toDouble()).toFloat()
        val clamped = compressed.coerceAtMost(LIMITER_CEILING_NORM)
        return if (sample >= 0f) clamped else -clamped
    }

    override fun onFlush() {
        // Preserva a continuidade da rampa em seeks ou transições gapless
        syncRampStateIfNeeded()
    }

    override fun onReset() {
        isTagGainLocked = false
        targetGainLinear = 1.0f
        currentGainLinear = 1.0f
        activeTargetGainLinear = 1.0f
        remainingRampFrames = 0
        gainStepPerFrame = 0f
        rampNeedsUpdate = false
    }
}
