package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.model.RingtoneOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

object SoundManager {
    private var currentJob: Job? = null
    private var currentTrack: AudioTrack? = null

    fun playRingtone(
        context: Context,
        ringtone: RingtoneOption,
        loop: Boolean = false,
        vibrate: Boolean = true,
        onComplete: (() -> Unit)? = null
    ) {
        stop()

        if (vibrate) {
            triggerVibration(context, ringtone)
        }

        currentJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                do {
                    val pcmData = generatePcmForOption(ringtone)
                    playPcm(pcmData)
                    if (loop) {
                        delay(600)
                    }
                } while (loop && isActive)
            } catch (_: Exception) {
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun stop() {
        currentJob?.cancel()
        currentJob = null
        try {
            currentTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {}
        currentTrack = null
    }

    private fun triggerVibration(context: Context, ringtone: RingtoneOption) {
        try {
            val pattern = when (ringtone) {
                RingtoneOption.RADAR_PULSE -> longArrayOf(0, 150, 100, 150, 300)
                RingtoneOption.CLASSIC_ALARM -> longArrayOf(0, 300, 150, 300, 200)
                RingtoneOption.ZEN_GONG -> longArrayOf(0, 600, 400)
                else -> longArrayOf(0, 250, 200, 250, 400)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.let {
                    val effect = VibrationEffect.createWaveform(pattern, -1)
                    it.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (_: Exception) {}
    }

    private fun playPcm(pcmShorts: ShortArray) {
        val sampleRate = 44100
        val bufferSize = pcmShorts.size * 2

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        currentTrack = audioTrack
        audioTrack.write(pcmShorts, 0, pcmShorts.size)
        audioTrack.play()

        // Wait for playback to finish
        val durationMillis = (pcmShorts.size.toDouble() / sampleRate * 1000).toLong()
        Thread.sleep(durationMillis)
        audioTrack.stop()
        audioTrack.release()
        currentTrack = null
    }

    private fun generatePcmForOption(option: RingtoneOption): ShortArray {
        val sampleRate = 44100
        return when (option) {
            RingtoneOption.RADAR_PULSE -> {
                // Alternating fast beeps: 950Hz, 1400Hz
                val totalSamples = sampleRate * 2
                val buffer = ShortArray(totalSamples)
                for (i in 0 until totalSamples) {
                    val timeSec = i.toDouble() / sampleRate
                    val freq = if ((i / 4000) % 2 == 0) 980.0 else 1450.0
                    val envelope = (1.0 - (i % 4000) / 4000.0).coerceIn(0.1, 1.0)
                    val sample = sin(2 * PI * freq * timeSec) * envelope
                    buffer[i] = (sample * 24000).toInt().toShort()
                }
                buffer
            }
            RingtoneOption.DIGITAL_CHIME -> {
                // Ascending melodic notes: C5 (523Hz), E5 (659Hz), G5 (784Hz), C6 (1046Hz)
                val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
                val noteDurationSec = 0.4
                val noteSamples = (sampleRate * noteDurationSec).toInt()
                val totalSamples = noteSamples * notes.size
                val buffer = ShortArray(totalSamples)

                for (n in notes.indices) {
                    val freq = notes[n]
                    for (i in 0 until noteSamples) {
                        val idx = n * noteSamples + i
                        val t = i.toDouble() / sampleRate
                        val decay = (1.0 - (i.toDouble() / noteSamples)).coerceAtLeast(0.0)
                        val sample = (sin(2 * PI * freq * t) + 0.4 * sin(4 * PI * freq * t)) * decay
                        buffer[idx] = (sample * 22000).toInt().toShort()
                    }
                }
                buffer
            }
            RingtoneOption.MORNING_BELLS -> {
                // Gentle bell chimes (warm chords with slow decay)
                val chords = listOf(
                    doubleArrayOf(440.0, 554.37, 659.25), // A Major
                    doubleArrayOf(493.88, 622.25, 739.99)  // B Major
                )
                val chordSamples = (sampleRate * 0.9).toInt()
                val totalSamples = chordSamples * chords.size
                val buffer = ShortArray(totalSamples)

                for (c in chords.indices) {
                    val chord = chords[c]
                    for (i in 0 until chordSamples) {
                        val idx = c * chordSamples + i
                        val t = i.toDouble() / sampleRate
                        val decay = Math.exp(-3.0 * i / chordSamples)
                        var sample = 0.0
                        chord.forEach { f -> sample += sin(2 * PI * f * t) }
                        sample /= chord.size
                        buffer[idx] = (sample * decay * 25000).toInt().toShort()
                    }
                }
                buffer
            }
            RingtoneOption.ENERGY_SYNTH -> {
                // Upbeat synth rhythm
                val totalSamples = sampleRate * 2
                val buffer = ShortArray(totalSamples)
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val step = (t * 8).toInt() % 4
                    val freq = when (step) {
                        0 -> 440.0
                        1 -> 523.25
                        2 -> 659.25
                        else -> 880.0
                    }
                    val sample = sin(2 * PI * freq * t)
                    buffer[i] = (sample * 23000).toInt().toShort()
                }
                buffer
            }
            RingtoneOption.ZEN_GONG -> {
                // Deep resonant bell gong (220Hz + harmonics)
                val totalSamples = (sampleRate * 2.5).toInt()
                val buffer = ShortArray(totalSamples)
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val decay = Math.exp(-1.8 * i / totalSamples)
                    val sample = 0.6 * sin(2 * PI * 220.0 * t) +
                                 0.3 * sin(2 * PI * 440.0 * t) +
                                 0.1 * sin(2 * PI * 880.0 * t)
                    buffer[i] = (sample * decay * 28000).toInt().toShort()
                }
                buffer
            }
            RingtoneOption.CLASSIC_ALARM -> {
                // Crisp mechanical double alarm tone
                val totalSamples = sampleRate * 2
                val buffer = ShortArray(totalSamples)
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val active = (i / 5000) % 2 == 0
                    val freq = 1200.0
                    val sample = if (active) sin(2 * PI * freq * t) else 0.0
                    buffer[i] = (sample * 26000).toInt().toShort()
                }
                buffer
            }
        }
    }
}
