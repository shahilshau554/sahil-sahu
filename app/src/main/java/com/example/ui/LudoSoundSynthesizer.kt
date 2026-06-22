package com.example.ui

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

object LudoSoundSynthesizer {

    fun playSound(frequencies: List<Float>, durationMs: Int, volume: Float = 0.5f) {
        Thread {
            try {
                val sampleRate = 44100
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val samples = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    
                    var sampleVal = 0.0
                    for (freq in frequencies) {
                        sampleVal += sin(2.0 * PI * freq * t)
                    }
                    if (frequencies.isNotEmpty()) {
                        sampleVal /= frequencies.size
                    }

                    // Linear fade out in last 15% to avoid clicking sounds
                    val fadeStartIndex = (numSamples * 0.85).toInt()
                    val amplitude = if (i > fadeStartIndex) {
                        val progress = (i - fadeStartIndex).toDouble() / (numSamples - fadeStartIndex)
                        (1.0 - progress) * volume
                    } else {
                        volume.toDouble()
                    }

                    samples[i] = (sampleVal * amplitude * Short.MAX_VALUE).toInt().toShort()
                }

                val audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    samples.size * 2,
                    AudioTrack.MODE_STATIC
                )

                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()
                
                Thread.sleep(durationMs.toLong() + 30)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun playDiceRollThump() {
        // A short low frequency thump to simulate rolling/rattling dice
        playSound(listOf(150f, 180f), 45, volume = 0.4f)
    }

    fun playDiceComplete() {
        // High pitched premium double tone chirp arpeggio, simulated by a quick frequency slide or layered frequencies
        playSound(listOf(659.25f, 987.77f, 1318.51f), 150, volume = 0.5f)
    }

    fun playStepPop() {
        // Very subtle short high pop to make piece movement satisfying
        playSound(listOf(800f, 1000f), 25, volume = 0.18f)
    }

    fun playCaptureSplash() {
        // Futuristic/retro down-sweep + noise explosion
        Thread {
            try {
                val sampleRate = 44100
                val durationMs = 450
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val samples = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val progress = i.toDouble() / numSamples
                    
                    // Frequencies sweep down dramatically
                    val freq = 1200.0 - (progress * 900.0)
                    val sine = sin(2.0 * PI * freq * t)
                    
                    // Add white noise for explosion texturing
                    val noise = if (i % 2 == 0) (Random.nextFloat() * 0.3f - 0.15f).toDouble() else 0.0
                    val combined = (sine + noise).coerceIn(-1.0, 1.0)
                    
                    // Decaying ADSR style envelope (very fast attack, constant decay)
                    val envelope = if (progress < 0.05) {
                        progress / 0.05
                    } else {
                        (1.0 - progress) * (1.0 - progress)
                    }

                    samples[i] = (combined * envelope * 0.7 * Short.MAX_VALUE).toInt().toShort()
                }

                val audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    samples.size * 2,
                    AudioTrack.MODE_STATIC
                )

                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()

                Thread.sleep(durationMs.toLong() + 30)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun playTimeOutSplash() {
        // Lower frequency buzz warning sound
        playSound(listOf(180f, 220f), 300, volume = 0.45f)
    }

    fun playTimerTick() {
        // Short soft high-pitched warning click
        playSound(listOf(1000f), 25, volume = 0.25f)
    }
}
