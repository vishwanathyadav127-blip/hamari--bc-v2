package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

object SirenSynthesizer {
  private const val SAMPLE_RATE = 22050
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var activeJob: Job? = null

  fun playChoonChoonSiren(cycles: Int = 5) {
    stop()
    activeJob = scope.launch {
      val chirpUp = synthesizeChirp(startFreq = 900.0, endFreq = 1950.0, durationMs = 150, volume = 0.85)
      val chirpHigh = synthesizeChirp(startFreq = 1050.0, endFreq = 2250.0, durationMs = 170, volume = 0.90)
      repeat(cycles) {
        if (!isActive) return@launch
        playPcmBuffer(chirpUp)
        delay(55L)
        if (!isActive) return@launch
        playPcmBuffer(chirpHigh)
        delay(230L)
      }
    }
  }

  fun playEndSiren() {
    stop()
    activeJob = scope.launch {
      val tone1 = synthesizeChirp(startFreq = 1046.0, endFreq = 987.0, durationMs = 200, volume = 0.85)
      val tone2 = synthesizeChirp(startFreq = 784.0, endFreq = 740.0, durationMs = 200, volume = 0.85)
      val tone3 = synthesizeChirp(startFreq = 587.0, endFreq = 440.0, durationMs = 460, volume = 0.90)
      playPcmBuffer(tone1)
      delay(60L)
      if (!isActive) return@launch
      playPcmBuffer(tone2)
      delay(60L)
      if (!isActive) return@launch
      playPcmBuffer(tone3)
    }
  }

  fun playBidPop() {
    scope.launch {
      val pop = synthesizeChirp(startFreq = 680.0, endFreq = 1120.0, durationMs = 65, volume = 0.45)
      playPcmBuffer(pop)
    }
  }

  fun stop() {
    activeJob?.cancel()
    activeJob = null
  }

  private fun synthesizeChirp(
    startFreq: Double,
    endFreq: Double,
    durationMs: Int,
    volume: Double
  ): ShortArray {
    val numSamples = (SAMPLE_RATE * durationMs) / 1000
    val samples = ShortArray(numSamples)
    var phase = 0.0
    val fadeSamples = (numSamples * 0.12).toInt().coerceAtLeast(1)

    for (i in 0 until numSamples) {
      val progress = i.toDouble() / numSamples
      val freq = startFreq + (endFreq - startFreq) * progress
      phase += 2.0 * PI * freq / SAMPLE_RATE

      val envelope = when {
        i < fadeSamples -> i.toDouble() / fadeSamples
        i > numSamples - fadeSamples -> (numSamples - i).toDouble() / fadeSamples
        else -> 1.0
      }

      // Fundamental + 2nd harmonic gives a crisp, loud mobile siren/chirp tone
      val wave = 0.72 * sin(phase) + 0.28 * sin(phase * 2.0)
      val value = (wave * envelope * volume * Short.MAX_VALUE).toInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
      samples[i] = value.toShort()
    }
    return samples
  }

  private suspend fun playPcmBuffer(samples: ShortArray) {
    runCatching {
      val minBufBytes = AudioTrack.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
      )
      val byteCount = samples.size * 2
      val bufferSize = maxOf(minBufBytes, byteCount)
      val track = AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        )
        .setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()

      try {
        track.write(samples, 0, samples.size)
        track.play()
        val durationMs = (samples.size * 1000L) / SAMPLE_RATE
        delay(durationMs + 25L)
      } finally {
        runCatching { track.stop() }
        runCatching { track.release() }
      }
    }
  }
}
