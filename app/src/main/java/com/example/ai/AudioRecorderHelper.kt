package com.example.ai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecorderHelper {
    private val sampleRate = 16000
    private val isRecording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var pcmBuffer = ByteArrayOutputStream()
    private var workerThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (isRecording.get()) return false
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (_: Exception) {
            null
        } ?: return false

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        pcmBuffer = ByteArrayOutputStream()
        audioRecord = record
        isRecording.set(true)
        record.startRecording()

        workerThread = Thread {
            val chunk = ByteArray(2048)
            while (isRecording.get()) {
                val read = record.read(chunk, 0, chunk.size)
                if (read > 0) {
                    synchronized(pcmBuffer) {
                        pcmBuffer.write(chunk, 0, read)
                    }
                }
            }
        }.apply { start() }
        return true
    }

    fun stopAndGetWav(): ByteArray? {
        if (!isRecording.getAndSet(false)) return null
        runCatching { workerThread?.join(250) }
        workerThread = null
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null

        val pcm = synchronized(pcmBuffer) { pcmBuffer.toByteArray() }
        // Ignore ultra-short accidental taps (< ~0.15s)
        if (pcm.size < 4800) return null
        return wrapWav(pcm, sampleRate, 1, 16)
    }

    private fun wrapWav(pcm: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val totalDataLen = pcm.size + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalDataLen)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1.toShort()) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * bitsPerSample / 8).toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray())
            putInt(pcm.size)
        }.array()
        return header + pcm
    }
}
