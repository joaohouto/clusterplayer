package com.joaohouto.clusterplayer.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.OpenableColumns
import android.util.Log
import androidx.media3.common.C
import com.joaohouto.clusterplayer.data.local.AppDatabase
import com.joaohouto.clusterplayer.data.local.entity.TrackLoudnessEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

data class LoudnessMeasurement(
    val integratedLufs: Float,
    val truePeak: Float
) {
    /**
     * Calcula o ganho em dB para atingir o nível-alvo (ex: -14.0 LUFS) + pré-amp opcional.
     */
    fun computeGainDb(
        targetLufs: Float = EbuR128Scanner.DEFAULT_TARGET_LUFS,
        preAmpDb: Float = 0f,
        minGainDb: Float = EbuR128Scanner.MIN_GAIN_DB,
        maxGainDb: Float = EbuR128Scanner.MAX_GAIN_DB
    ): Float {
        if (!integratedLufs.isFinite() || integratedLufs <= EbuR128Scanner.ABSOLUTE_GATE_LUFS) {
            return 0f
        }
        val rawGainDb = (targetLufs - integratedLufs) + preAmpDb
        return rawGainDb.coerceIn(minGainDb, maxGainDb)
    }
}

/**
 * Medidor de sonoridade integrada em conformidade com ITU-R BS.1770-4 / EBU R128 (Tech 3341).
 *
 * Implementa:
 * 1. Filtro K-Weighting em 2 estágios (High-Shelf + RLB High-Pass) com coeficientes calculados
 *    via transformada bilinear para qualquer taxa de amostragem (44.1 kHz, 48 kHz, 96 kHz, etc.).
 * 2. Janelas de gating de 400ms com sobreposição de 75% (passo de 100ms).
 * 3. Gate absoluto em -70.0 LUFS e gate relativo em -10.0 LU.
 * 4. Medição simultânea de pico amostral normalizado (0.0..1.0+).
 */
class EbuR128Meter(
    private val sampleRate: Int,
    private val channelCount: Int
) {
    // Coeficientes biquad do Estágio 1 (High-Shelf Pre-Filter)
    private val s1B0: Double
    private val s1B1: Double
    private val s1B2: Double
    private val s1A1: Double
    private val s1A2: Double

    // Coeficientes biquad do Estágio 2 (RLB High-Pass Filter)
    private val s2B0: Double
    private val s2B1: Double
    private val s2B2: Double
    private val s2A1: Double
    private val s2A2: Double

    // Estados Direct Form II Transposed por canal
    private val s1Z1 = DoubleArray(channelCount)
    private val s1Z2 = DoubleArray(channelCount)
    private val s2Z1 = DoubleArray(channelCount)
    private val s2Z2 = DoubleArray(channelCount)

    // Sub-blocos de 100ms (4 sub-blocos formam uma janela de 400ms com passo de 100ms)
    private val stepFrames: Int = max(1, sampleRate / 10)
    private val subBlockEnergies = DoubleArray(4)
    private var subBlockCount = 0
    private var currentStepFrames = 0
    private var currentSubBlockSum = 0.0

    // Armazenamento compacto das potências dos blocos de 400ms que superam o gate absoluto (-70 LUFS)
    private var gatedBlockPowers = DoubleArray(2048)
    private var gatedBlockCount = 0

    // Energia total acumulada (fallback para sinais mais curtos que 400ms)
    private var totalWeightedEnergySum = 0.0
    private var totalFramesProcessed = 0L

    private var maxAbsSample = 0f

    init {
        val fs = sampleRate.toDouble()

        // Estágio 1: High-Shelf conforme especificação analógica ITU-R BS.1770-4 via transformada bilinear
        val f0Shelf = 1681.974450955533
        val gShelfDb = 3.999843853973347
        val qShelf = 0.7071752369554196
        val kShelf = tan(Math.PI * f0Shelf / fs)
        val vh = 10.0.pow(gShelfDb / 20.0)
        val vb = vh.pow(0.4996667741545416)
        val kShelfSq = kShelf * kShelf
        val a0Shelf = 1.0 + kShelf / qShelf + kShelfSq

        s1B0 = (vh + vb * kShelf / qShelf + kShelfSq) / a0Shelf
        s1B1 = 2.0 * (kShelfSq - vh) / a0Shelf
        s1B2 = (vh - vb * kShelf / qShelf + kShelfSq) / a0Shelf
        s1A1 = 2.0 * (kShelfSq - 1.0) / a0Shelf
        s1A2 = (1.0 - kShelf / qShelf + kShelfSq) / a0Shelf

        // Estágio 2: RLB High-Pass conforme especificação analógica ITU-R BS.1770-4 via transformada bilinear
        val f0Hp = 38.13547087602444
        val qHp = 0.5003270373238773
        val kHp = tan(Math.PI * f0Hp / fs)
        val kHpSq = kHp * kHp
        val a0Hp = 1.0 + kHp / qHp + kHpSq

        s2B0 = 1.0 / a0Hp
        s2B1 = -2.0 / a0Hp
        s2B2 = 1.0 / a0Hp
        s2A1 = 2.0 * (kHpSq - 1.0) / a0Hp
        s2A2 = (1.0 - kHp / qHp + kHpSq) / a0Hp
    }

    /**
     * Processa amostras PCM de 16 bits intercaladas (little-endian).
     */
    fun processPcm16(buffer: ByteBuffer) {
        val bytesPerFrame = channelCount * 2
        val availableFrames = buffer.remaining() / bytesPerFrame
        if (availableFrames <= 0) return

        val invScale = 1.0 / 32768.0
        // Peso por canal: se for mono (1 canal), em reprodução estéreo o sinal é duplicado para L+R (peso 2.0)
        val channelWeight = if (channelCount == 1) 2.0 else 1.0

        for (f in 0 until availableFrames) {
            var frameEnergy = 0.0
            for (ch in 0 until channelCount) {
                val rawShort = buffer.short.toInt()
                val normalized = (rawShort * invScale).toFloat()
                val absVal = abs(normalized)
                if (absVal > maxAbsSample) {
                    maxAbsSample = absVal
                }

                // Apenas os 5 primeiros canais participam da soma ponderada (L, R, C = 1.0; Ls, Rs = 1.41)
                val weight = when {
                    channelCount == 1 -> channelWeight
                    ch < 3 -> 1.0
                    ch in 3..4 -> 1.41
                    else -> 0.0
                }
                if (weight > 0.0) {
                    val x = normalized.toDouble()
                    // Biquad Estágio 1
                    val y1 = s1B0 * x + s1Z1[ch]
                    s1Z1[ch] = s1B1 * x - s1A1 * y1 + s1Z2[ch]
                    s1Z2[ch] = s1B2 * x - s1A2 * y1

                    // Biquad Estágio 2
                    val y2 = s2B0 * y1 + s2Z1[ch]
                    s2Z1[ch] = s2B1 * y1 - s2A1 * y2 + s2Z2[ch]
                    s2Z2[ch] = s2B2 * y1 - s2A2 * y2

                    frameEnergy += weight * (y2 * y2)
                }
            }
            onFrameEnergy(frameEnergy)
        }
    }

    /**
     * Processa amostras PCM Float intercaladas (escala nominal [-1.0, +1.0]).
     */
    fun processPcmFloat(buffer: ByteBuffer) {
        val bytesPerFrame = channelCount * 4
        val availableFrames = buffer.remaining() / bytesPerFrame
        if (availableFrames <= 0) return

        val channelWeight = if (channelCount == 1) 2.0 else 1.0

        for (f in 0 until availableFrames) {
            var frameEnergy = 0.0
            for (ch in 0 until channelCount) {
                val sample = buffer.float
                if (!sample.isFinite()) continue
                val absVal = abs(sample)
                if (absVal > maxAbsSample) {
                    maxAbsSample = absVal
                }

                val weight = when {
                    channelCount == 1 -> channelWeight
                    ch < 3 -> 1.0
                    ch in 3..4 -> 1.41
                    else -> 0.0
                }
                if (weight > 0.0) {
                    val x = sample.toDouble()
                    val y1 = s1B0 * x + s1Z1[ch]
                    s1Z1[ch] = s1B1 * x - s1A1 * y1 + s1Z2[ch]
                    s1Z2[ch] = s1B2 * x - s1A2 * y1

                    val y2 = s2B0 * y1 + s2Z1[ch]
                    s2Z1[ch] = s2B1 * y1 - s2A1 * y2 + s2Z2[ch]
                    s2Z2[ch] = s2B2 * y1 - s2A2 * y2

                    frameEnergy += weight * (y2 * y2)
                }
            }
            onFrameEnergy(frameEnergy)
        }
    }

    private fun onFrameEnergy(frameEnergy: Double) {
        currentSubBlockSum += frameEnergy
        totalWeightedEnergySum += frameEnergy
        totalFramesProcessed++
        currentStepFrames++

        if (currentStepFrames >= stepFrames) {
            val meanSubBlockPower = currentSubBlockSum / currentStepFrames.toDouble()
            subBlockEnergies[subBlockCount % 4] = meanSubBlockPower
            subBlockCount++
            currentSubBlockSum = 0.0
            currentStepFrames = 0

            // Quando temos pelo menos 4 sub-blocos (400ms), avaliamos o bloco de gating
            if (subBlockCount >= 4) {
                val blockPower = 0.25 * (
                    subBlockEnergies[0] +
                        subBlockEnergies[1] +
                        subBlockEnergies[2] +
                        subBlockEnergies[3]
                    )
                if (blockPower > EbuR128Scanner.ABSOLUTE_GATE_POWER_THRESHOLD) {
                    if (gatedBlockCount == gatedBlockPowers.size) {
                        gatedBlockPowers = gatedBlockPowers.copyOf(gatedBlockPowers.size * 2)
                    }
                    gatedBlockPowers[gatedBlockCount++] = blockPower
                }
            }
        }
    }

    fun finish(): LoudnessMeasurement {
        if (gatedBlockCount == 0) {
            if (totalFramesProcessed > 0L && totalWeightedEnergySum > 0.0) {
                val meanPower = totalWeightedEnergySum / totalFramesProcessed.toDouble()
                val lufs = (-0.691 + 10.0 * log10(meanPower)).toFloat()
                return LoudnessMeasurement(
                    integratedLufs = lufs.coerceAtLeast(EbuR128Scanner.ABSOLUTE_GATE_LUFS),
                    truePeak = maxAbsSample
                )
            }
            return LoudnessMeasurement(
                integratedLufs = EbuR128Scanner.ABSOLUTE_GATE_LUFS,
                truePeak = maxAbsSample
            )
        }

        // 1. Média dos blocos que superaram o Gate Absoluto (-70 LUFS)
        var absGateSum = 0.0
        for (i in 0 until gatedBlockCount) {
            absGateSum += gatedBlockPowers[i]
        }
        val absGateMeanPower = absGateSum / gatedBlockCount.toDouble()

        // 2. Limiar do Gate Relativo (-10 LU abaixo da média do gate absoluto => fator 0.1 em potência)
        val relativeGatePowerThreshold = absGateMeanPower * 0.1

        var relGateSum = 0.0
        var relGateCount = 0
        for (i in 0 until gatedBlockCount) {
            val p = gatedBlockPowers[i]
            if (p >= relativeGatePowerThreshold) {
                relGateSum += p
                relGateCount++
            }
        }

        val finalMeanPower = if (relGateCount > 0) {
            relGateSum / relGateCount.toDouble()
        } else {
            absGateMeanPower
        }

        val integratedLufs = (-0.691 + 10.0 * log10(finalMeanPower)).toFloat()
        return LoudnessMeasurement(
            integratedLufs = integratedLufs,
            truePeak = maxAbsSample
        )
    }
}

object EbuR128Scanner {
    private const val TAG = "EbuR128Scanner"

    const val ALGORITHM_VERSION = 1
    const val DEFAULT_TARGET_LUFS = -14.0f
    const val ABSOLUTE_GATE_LUFS = -70.0f
    const val MIN_GAIN_DB = -9.0f
    const val MAX_GAIN_DB = 9.0f

    // Limiar linear de potência correspondente a -70.0 LUFS: 10^((-70 + 0.691) / 10)
    val ABSOLUTE_GATE_POWER_THRESHOLD: Double = 10.0.pow((-70.0 + 0.691) / 10.0)

    private const val CODEC_TIMEOUT_US = 10_000L
    private val DB_VALUE_REGEX = Regex("""([+-]?\d+(?:\.\d+)?)""")

    /**
     * Interpreta o valor de uma tag REPLAYGAIN_TRACK_GAIN ou R128_TRACK_GAIN.
     * - REPLAYGAIN_TRACK_GAIN: valor em dB (ex: "-5.20 dB").
     * - R128_TRACK_GAIN: inteiro Q7.8 relativo a -23.0 LUFS (ex: "-1280" => -5.0 dB @ -23 LUFS)
     *   ou string em dB, ajustado para o `targetLufs` (ex: -14.0 LUFS).
     */
    fun parseReplayGainOrR128Tag(
        key: String,
        rawValue: String,
        targetLufs: Float = DEFAULT_TARGET_LUFS
    ): Float? {
        val cleanKey = key.trim()
        val cleanVal = rawValue.trim()
        if (cleanVal.isEmpty()) return null

        if (cleanKey.equals("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)) {
            val match = DB_VALUE_REGEX.find(cleanVal) ?: return null
            val db = match.groupValues[1].toFloatOrNull() ?: return null
            return db.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }

        if (cleanKey.equals("R128_TRACK_GAIN", ignoreCase = true)) {
            val r128RefOffset = targetLufs - (-23.0f)
            if (cleanVal.contains("dB", ignoreCase = true) || cleanVal.contains('.')) {
                val match = DB_VALUE_REGEX.find(cleanVal) ?: return null
                val dbAt23 = match.groupValues[1].toFloatOrNull() ?: return null
                return (dbAt23 + r128RefOffset).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
            }
            val q78 = cleanVal.toIntOrNull() ?: return null
            val dbAt23 = q78.toFloat() / 256.0f
            return (dbAt23 + r128RefOffset).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }

        return null
    }

    /**
     * Extrai ganho de faixa a partir de metadados do ExoPlayer (ID3 TXXX, VorbisComment, etc.).
     */
    fun extractTagGainFromMetadata(
        metadata: androidx.media3.common.Metadata?,
        targetLufs: Float = DEFAULT_TARGET_LUFS
    ): Float? {
        if (metadata == null) return null
        for (i in 0 until metadata.length()) {
            val entry = metadata.get(i)
            when (entry) {
                is androidx.media3.extractor.metadata.id3.TextInformationFrame -> {
                    val desc = entry.description ?: ""
                    val value = entry.values.firstOrNull() ?: ""
                    parseReplayGainOrR128Tag(desc, value, targetLufs)?.let { return it }
                }
                is androidx.media3.extractor.metadata.vorbis.VorbisComment -> {
                    parseReplayGainOrR128Tag(entry.key, entry.value, targetLufs)?.let { return it }
                }
                is androidx.media3.extractor.metadata.flac.VorbisComment -> {
                    parseReplayGainOrR128Tag(entry.key, entry.value, targetLufs)?.let { return it }
                }
                else -> {
                    val text = entry.toString()
                    if (text.contains("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)) {
                        val afterKey = text.substringAfter("REPLAYGAIN_TRACK_GAIN", "")
                        parseReplayGainOrR128Tag("REPLAYGAIN_TRACK_GAIN", afterKey, targetLufs)?.let { return it }
                    } else if (text.contains("R128_TRACK_GAIN", ignoreCase = true)) {
                        val afterKey = text.substringAfter("R128_TRACK_GAIN", "").trimStart('=', ':', ' ')
                        parseReplayGainOrR128Tag("R128_TRACK_GAIN", afterKey, targetLufs)?.let { return it }
                    }
                }
            }
        }
        return null
    }

    /**
     * Resolve tamanho e data de modificação do arquivo para chave de invalidação do cache.
     */
    fun resolveFileIdentity(context: Context, uriString: String, filePath: String): Pair<Long, Long> {
        if (filePath.isNotEmpty()) {
            try {
                val f = File(filePath)
                if (f.exists()) {
                    return Pair(f.length(), f.lastModified())
                }
            } catch (_: Exception) {}
        }
        if (uriString.startsWith("file://")) {
            try {
                val path = Uri.parse(uriString).path
                if (!path.isNullOrEmpty()) {
                    val f = File(path)
                    if (f.exists()) {
                        return Pair(f.length(), f.lastModified())
                    }
                }
            } catch (_: Exception) {}
        }
        if (uriString.startsWith("content://")) {
            try {
                val uri = Uri.parse(uriString)
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                        val size = if (sizeIdx != -1 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else 0L
                        return Pair(size, 0L)
                    }
                }
            } catch (_: Exception) {}
        }
        return Pair(0L, 0L)
    }

    /**
     * Decodifica a faixa inteira via MediaExtractor + MediaCodec em thread de baixa prioridade
     * e calcula o LUFS integrado e o pico máximo conforme EBU R128 / ITU-R BS.1770-4.
     */
    suspend fun scanTrack(
        context: Context,
        uriString: String,
        filePath: String
    ): LoudnessMeasurement? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var pfd: android.os.ParcelFileDescriptor? = null

        try {
            // Definir prioridade de background para não competir com o decoder de reprodução
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            } catch (_: Throwable) {}

            var dataSourceSet = false
            if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                try {
                    pfd = context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")
                    if (pfd != null) {
                        extractor.setDataSource(pfd.fileDescriptor)
                        dataSourceSet = true
                    }
                } catch (_: Exception) {}
            }

            if (!dataSourceSet && filePath.isNotEmpty()) {
                val file = File(filePath)
                if (file.exists()) {
                    extractor.setDataSource(file.absolutePath)
                    dataSourceSet = true
                }
            }

            if (!dataSourceSet && uriString.startsWith("/")) {
                val file = File(uriString)
                if (file.exists()) {
                    extractor.setDataSource(file.absolutePath)
                    dataSourceSet = true
                }
            }

            if (!dataSourceSet) {
                return null
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            var mimeType: String? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    mimeType = mime
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null || mimeType == null) {
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            var sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            var channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2
            var pcmEncoding = C.ENCODING_PCM_16BIT

            var meter = EbuR128Meter(sampleRate.coerceAtLeast(8000), channelCount.coerceAtLeast(1))

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                coroutineContext.ensureActive()

                if (!inputDone) {
                    val inputBufferIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    presentationTimeUs.coerceAtLeast(0L),
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                when {
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outputBuffer.order(ByteOrder.nativeOrder())

                            if (pcmEncoding == C.ENCODING_PCM_FLOAT) {
                                meter.processPcmFloat(outputBuffer)
                            } else {
                                meter.processPcm16(outputBuffer)
                            }
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        val newSampleRate = if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        } else sampleRate
                        val newChannels = if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        } else channelCount
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                            newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)
                        ) {
                            pcmEncoding = newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                        if (newSampleRate != sampleRate || newChannels != channelCount) {
                            sampleRate = newSampleRate.coerceAtLeast(8000)
                            channelCount = newChannels.coerceAtLeast(1)
                            meter = EbuR128Meter(sampleRate, channelCount)
                        }
                    }
                }
            }

            return meter.finish()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to scan EBU R128 loudness for $uriString: ${e.message}")
            return null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {}
            try {
                codec?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
            try {
                pfd?.close()
            } catch (_: Exception) {}
        }
    }
}

/**
 * Coordenador de análise EBU R128 e cache em banco Room (`track_loudness`).
 *
 * - Garante concorrência máxima = 1 em `THREAD_PRIORITY_BACKGROUND`.
 * - Cancela análises antigas ao pular de faixa para priorizar a faixa atual e a próxima da fila.
 * - Executa varredura gradual em lote da biblioteca quando ocioso.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoudnessAnalysisManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LoudnessAnalysisMgr"

        @Volatile
        private var INSTANCE: LoudnessAnalysisManager? = null

        fun getInstance(context: Context): LoudnessAnalysisManager {
            return INSTANCE ?: synchronized(this) {
                val instance = LoudnessAnalysisManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val database = AppDatabase.getInstance(context)
    private val loudnessDao = database.trackLoudnessDao()
    private val trackDao = database.trackDao()

    // Cache rápido em memória para consulta imediata na transição de faixa
    private val memoryCache = ConcurrentHashMap<String, TrackLoudnessEntity>()

    // Dispatcher dedicado com paralelismo = 1 para nunca competir com a decodificação do ExoPlayer
    private val analyzerDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var activePriorityJob: Job? = null
    private var batchLibraryJob: Job? = null

    /**
     * Consulta o cache em memória ou Room validando versão do algoritmo e identidade do arquivo.
     */
    suspend fun getValidCachedMeasurement(uri: String, filePath: String): LoudnessMeasurement? {
        if (uri.isEmpty()) return null
        val (fileSize, lastModified) = withContext(Dispatchers.IO) {
            EbuR128Scanner.resolveFileIdentity(context, uri, filePath)
        }

        val inMem = memoryCache[uri]
        if (inMem != null && isEntityValid(inMem, fileSize, lastModified)) {
            return LoudnessMeasurement(inMem.integratedLufs, inMem.truePeak)
        }

        val dbEntity = withContext(Dispatchers.IO) {
            loudnessDao.getLoudnessByUri(uri)
        }
        if (dbEntity != null && isEntityValid(dbEntity, fileSize, lastModified)) {
            memoryCache[uri] = dbEntity
            return LoudnessMeasurement(dbEntity.integratedLufs, dbEntity.truePeak)
        }

        return null
    }

    private fun isEntityValid(entity: TrackLoudnessEntity, currentSize: Long, currentLastModified: Long): Boolean {
        if (entity.algorithmVersion != EbuR128Scanner.ALGORITHM_VERSION) return false
        if (currentSize > 0L && entity.fileSize > 0L && entity.fileSize != currentSize) return false
        if (currentLastModified > 0L && entity.lastModified > 0L && entity.lastModified != currentLastModified) return false
        return entity.integratedLufs.isFinite()
    }

    /**
     * Prioriza a análise da faixa atual (se não estiver em cache) e pré-analisa a próxima faixa da fila.
     * Cancela imediatamente qualquer análise de faixa anterior se o usuário tiver pulado de música.
     */
    fun analyzeCurrentAndNextTrack(
        currentUri: String,
        currentFilePath: String?,
        nextUri: String?,
        nextFilePath: String?,
        onCurrentTrackMeasured: (uri: String, measurement: LoudnessMeasurement, fromCache: Boolean) -> Unit
    ) {
        activePriorityJob?.cancel()
        activePriorityJob = scope.launch {
            val safeCurrentPath = currentFilePath ?: ""
            // 1. Faixa atual
            if (currentUri.isNotEmpty()) {
                val cached = getValidCachedMeasurement(currentUri, safeCurrentPath)
                if (cached != null) {
                    onCurrentTrackMeasured(currentUri, cached, true)
                } else {
                    val startMs = android.os.SystemClock.elapsedRealtime()
                    val measured = withContext(analyzerDispatcher) {
                        scanAndPersist(currentUri, safeCurrentPath)
                    }
                    if (measured != null) {
                        val elapsedMs = android.os.SystemClock.elapsedRealtime() - startMs
                        Log.d(
                            TAG,
                            "Current track EBU R128 analyzed in ${elapsedMs}ms: lufs=${"%.2f".format(measured.integratedLufs)}, peak=${"%.3f".format(measured.truePeak)} ($currentUri)"
                        )
                        onCurrentTrackMeasured(currentUri, measured, false)
                    }
                }
            }

            // 2. Próxima faixa da fila (pré-análise antecipada)
            if (!nextUri.isNullOrEmpty() && nextUri != currentUri) {
                val nextCached = getValidCachedMeasurement(nextUri, nextFilePath ?: "")
                if (nextCached == null) {
                    val startMs = android.os.SystemClock.elapsedRealtime()
                    val measured = withContext(analyzerDispatcher) {
                        scanAndPersist(nextUri, nextFilePath ?: "")
                    }
                    if (measured != null) {
                        val elapsedMs = android.os.SystemClock.elapsedRealtime() - startMs
                        Log.d(
                            TAG,
                            "Next track pre-analyzed in ${elapsedMs}ms: lufs=${"%.2f".format(measured.integratedLufs)}, peak=${"%.3f".format(measured.truePeak)} ($nextUri)"
                        )
                    }
                }
            }
        }
    }

    /**
     * Inicia (ou retoma) a análise em lote de todas as faixas da biblioteca em segundo plano
     * após o término do escaneamento de armazenamento.
     * Como compartilha o `analyzerDispatcher` (limitedParallelism = 1) mas roda faixa a faixa,
     * cede espaço automaticamente sempre que `analyzeCurrentAndNextTrack` solicitar o dispatcher.
     */
    fun triggerLibraryBatchAnalysis() {
        if (batchLibraryJob?.isActive == true) return
        batchLibraryJob = scope.launch {
            try {
                val allTracks = trackDao.getAllTracksSync()
                if (allTracks.isEmpty()) return@launch
                val cachedUris = loudnessDao.getAllCachedUrisForVersion(EbuR128Scanner.ALGORITHM_VERSION).toHashSet()
                val pendingTracks = allTracks.filter { it.uri !in cachedUris }
                if (pendingTracks.isEmpty()) return@launch

                Log.d(TAG, "Starting background EBU R128 batch analysis for ${pendingTracks.size} uncached tracks...")
                var analyzedCount = 0
                for (track in pendingTracks) {
                    ensureActive()
                    // Se uma faixa prioritária estiver sendo analisada, aguarda antes de submeter ao dispatcher único
                    activePriorityJob?.join()
                    val existing = getValidCachedMeasurement(track.uri, track.path)
                    if (existing == null) {
                        withContext(analyzerDispatcher) {
                            scanAndPersist(track.uri, track.path)
                        }
                        analyzedCount++
                    }
                }
                Log.d(TAG, "Background EBU R128 batch analysis finished ($analyzedCount tracks newly cached).")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Error during library batch loudness analysis", e)
            }
        }
    }

    private suspend fun scanAndPersist(uri: String, filePath: String): LoudnessMeasurement? {
        val measurement = EbuR128Scanner.scanTrack(context, uri, filePath) ?: return null
        val (fileSize, lastModified) = EbuR128Scanner.resolveFileIdentity(context, uri, filePath)
        val entity = TrackLoudnessEntity(
            uri = uri,
            fileSize = fileSize,
            lastModified = lastModified,
            integratedLufs = measurement.integratedLufs,
            truePeak = measurement.truePeak,
            algorithmVersion = EbuR128Scanner.ALGORITHM_VERSION,
            updatedAt = System.currentTimeMillis()
        )
        memoryCache[uri] = entity
        loudnessDao.upsertLoudness(entity)
        return measurement
    }
}
