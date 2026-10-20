package com.joaohouto.clusterplayer.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class EbuR128AndNormalizerTest {

    private fun generateStereoSinePcm16Buffer(
        sampleRate: Int,
        durationSec: Double,
        freqHz: Double,
        amplitudeLinear: Double
    ): ByteBuffer {
        val totalFrames = (sampleRate * durationSec).toInt()
        val buffer = ByteBuffer.allocate(totalFrames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (n in 0 until totalFrames) {
            val value = sin(2.0 * PI * freqHz * n / sampleRate) * amplitudeLinear
            val pcm16 = (value * 32768.0).roundToInt().coerceIn(-32768, 32767).toShort()
            buffer.putShort(pcm16)
            buffer.putShort(pcm16)
        }
        buffer.flip()
        return buffer
    }

    @Test
    fun ebuR128Meter_reference1kHzSineAtMinus23Lufs_48kHz() {
        val sampleRate = 48000
        // For stereo in-phase 1 kHz sine wave, 0 LUFS corresponds to 0 dBFS peak (amplitude = 1.0)
        val amplitude = 10.0.pow(-23.0 / 20.0)
        val buffer = generateStereoSinePcm16Buffer(sampleRate, durationSec = 3.0, freqHz = 1000.0, amplitudeLinear = amplitude)

        val meter = EbuR128Meter(sampleRate = sampleRate, channelCount = 2)
        meter.processPcm16(buffer)
        val result = meter.finish()

        assertEquals(-23.0f, result.integratedLufs, 0.1f)
        assertEquals(amplitude.toFloat(), result.truePeak, 0.005f)
    }

    @Test
    fun ebuR128Meter_reference1kHzSineAtMinus23Lufs_44100Hz_and_96000Hz() {
        val amplitude = 10.0.pow(-23.0 / 20.0)

        for (sampleRate in listOf(44100, 96000)) {
            val buffer = generateStereoSinePcm16Buffer(sampleRate, durationSec = 3.0, freqHz = 1000.0, amplitudeLinear = amplitude)
            val meter = EbuR128Meter(sampleRate = sampleRate, channelCount = 2)
            meter.processPcm16(buffer)
            val result = meter.finish()

            assertEquals("Mismatch at $sampleRate Hz", -23.0f, result.integratedLufs, 0.1f)
        }
    }

    @Test
    fun ebuR128Meter_relativeAndAbsoluteGating_ignoresQuietIntroAndSilence() {
        val sampleRate = 48000
        // 2 seconds of -50 LUFS (quiet intro, >10 LU below main section) + 6 seconds of -18 LUFS (main section)
        val quietAmp = 10.0.pow(-50.0 / 20.0)
        val mainAmp = 10.0.pow(-18.0 / 20.0)

        val quietBuffer = generateStereoSinePcm16Buffer(sampleRate, durationSec = 2.0, freqHz = 1000.0, amplitudeLinear = quietAmp)
        val mainBuffer = generateStereoSinePcm16Buffer(sampleRate, durationSec = 6.0, freqHz = 1000.0, amplitudeLinear = mainAmp)

        val meter = EbuR128Meter(sampleRate = sampleRate, channelCount = 2)
        meter.processPcm16(quietBuffer)
        meter.processPcm16(mainBuffer)
        val result = meter.finish()

        // Relative gate (-10 LU) must exclude the 17 pure -50 LUFS intro blocks
        // (leaving 57 pure -18 LUFS blocks + 3 boundary overlap blocks => ~ -18.11 LUFS instead of -19.2 LUFS ungated)
        assertEquals(-18.1f, result.integratedLufs, 0.2f)
    }

    @Test
    fun parseReplayGainOrR128Tag_supportsReplayGainAndR128Q7_8() {
        val rgGain = EbuR128Scanner.parseReplayGainOrR128Tag("REPLAYGAIN_TRACK_GAIN", "-6.50 dB")
        assertNotNull(rgGain)
        assertEquals(-6.50f, rgGain!!, 0.01f)

        // R128_TRACK_GAIN in Q7.8 fixed point: -768 / 256 = -3.0 dB at -23 LUFS -> +6.0 dB at -14 LUFS
        val r128Gain = EbuR128Scanner.parseReplayGainOrR128Tag("R128_TRACK_GAIN", "-768")
        assertNotNull(r128Gain)
        assertEquals(6.0f, r128Gain!!, 0.05f)
    }

    @Test
    fun volumeNormalizer_maintainsConstantGainAndAppliesSmoothRamp_16BitAndFloat() {
        val processor = VolumeNormalizerAudioProcessor()
        processor.isNormalizerEnabled = true
        processor.configure(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        // Start at 0 dB (1.0x), then ramp to -6.02 dB (~0.5x) over 100ms
        processor.setTrackGainDb(-6.0206f, rampDurationMs = 100, fromTag = false)

        // Feed 200ms of constant 10000 amplitude PCM 16-bit samples
        val totalFrames = 4800 * 2 // 200ms at 48kHz
        val inputBuffer = ByteBuffer.allocateDirect(totalFrames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalFrames * 2) {
            inputBuffer.putShort(10000)
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)

        // First sample should start near 10000 (1.0x)
        val firstSample = output.getShort(0).toInt()
        // Last sample (after 200ms > 100ms ramp) should settle at ~5000 (0.5x) and stay constant
        val midAfterRampSample = output.getShort((4800 * 1.2).toInt() * 4).toInt()
        val lastSample = output.getShort((totalFrames - 1) * 4).toInt()

        assertTrue("First sample should be close to 10000, was $firstSample", firstSample in 9900..10000)
        assertEquals("Gain after ramp must be constant (no pumping)", midAfterRampSample, lastSample)
        assertTrue("Settled sample should be around 5000, was $lastSample", lastSample in 4950..5050)
    }

    @Test
    fun volumeNormalizer_softKneePeakLimiter_preventsHardClippingInFloatMode() {
        val processor = VolumeNormalizerAudioProcessor()
        processor.isNormalizerEnabled = true
        processor.configure(AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()

        // Apply +6 dB (~2.0x) with immediate ramp (0ms)
        processor.setTrackGainDb(6.0f, rampDurationMs = 0, fromTag = true)

        val inputBuffer = ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.putFloat(0.85f)
        inputBuffer.putFloat(-0.85f)
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        val outPos = output.getFloat(0)
        val outNeg = output.getFloat(4)

        assertTrue("Positive peak must be soft-limited below 1.0f, was $outPos", outPos in 0.90f..0.9999f)
        assertTrue("Negative peak must be soft-limited above -1.0f, was $outNeg", outNeg in -0.9999f..-0.90f)
        assertEquals(abs(outPos), abs(outNeg), 0.0001f)
    }
}
