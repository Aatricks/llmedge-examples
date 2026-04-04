package com.example.llmedgeexample.demo.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import io.aatricks.llmedge.speech.tts.BarkTTS
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object TTSAudioSupport {
    fun buildAudioTrack(audio: BarkTTS.AudioResult): AudioTrack {
        val pcmData = ShortArray(audio.samples.size)
        for (i in audio.samples.indices) {
            val sample = audio.samples[i].coerceIn(-1.0f, 1.0f)
            pcmData[i] = (sample * 32767).toInt().toShort()
        }

        val bufferSize =
            AudioTrack.getMinBufferSize(
                audio.sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize.coerceAtLeast(pcmData.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .also { track ->
                track.write(pcmData, 0, pcmData.size)
            }
    }

    fun saveAsWav(
        samples: FloatArray,
        sampleRate: Int,
        outputFile: File,
    ) {
        outputFile.parentFile?.mkdirs()

        FileOutputStream(outputFile).use { fos ->
            fos.write(createWavHeader(samples.size, sampleRate))
            val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                val clamped = sample.coerceIn(-1.0f, 1.0f)
                buffer.putShort((clamped * 32767.0f).toInt().toShort())
            }
            fos.write(buffer.array())
        }
    }

    private fun createWavHeader(
        numSamples: Int,
        sampleRate: Int,
    ): ByteArray {
        val byteRate = sampleRate * 2
        val dataSize = numSamples * 2
        val fileSize = 36 + dataSize

        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(fileSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        return buffer.array()
    }
}
