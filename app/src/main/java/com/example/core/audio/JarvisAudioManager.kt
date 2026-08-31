package com.example.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Manages raw audio recording (PCM 16kHz 16-bit Mono), streaming playback via AudioTrack,
 * voice activity / amplitude calculation, volume management, and instant interruption / barge-in.
 */
class JarvisAudioManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val tag = "JarvisAudioManager"

    companion object {
        const val SAMPLE_RATE_IN_HZ = 16000 // Standard for speech / Gemini Live
        const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_CHUNK_SIZE = 1024 // Samples per chunk
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    private var audioTrack: AudioTrack? = null

    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _rmsAmplitude = MutableStateFlow(0f)
    val rmsAmplitude: StateFlow<Float> = _rmsAmplitude.asStateFlow()

    var onAudioChunkCaptured: ((ByteArray) -> Unit)? = null
    var onVoiceActivityDetected: (() -> Unit)? = null

    // Threshold for voice activity detection during playback (Interruption / Barge-in)
    private val voiceActivityThreshold = 0.18f

    /**
     * Starts continuous background audio recording from the microphone.
     */
    fun startRecording() {
        if (_isRecording.value) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_IN_HZ,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT
        ).coerceAtLeast(BUFFER_CHUNK_SIZE * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_IN_HZ,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                minBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord initialization failed. State != INITIALIZED")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            _isRecording.value = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(BUFFER_CHUNK_SIZE * 2) // 2 bytes per 16-bit sample

                while (isActive && _isRecording.value) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readBytes > 0) {
                        val chunk = buffer.copyOf(readBytes)
                        val amp = calculateRmsAmplitude(chunk, readBytes)
                        _rmsAmplitude.value = amp

                        // Trigger barge-in if voice is loud enough while AI is speaking
                        if (_isPlaying.value && amp > voiceActivityThreshold) {
                            Log.d(tag, "User interruption detected (amplitude: $amp) -> trigger barge-in")
                            withContext(Dispatchers.Main) {
                                onVoiceActivityDetected?.invoke()
                            }
                        }

                        onAudioChunkCaptured?.invoke(chunk)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(tag, "Record Audio permission missing: ${e.message}")
            stopRecording()
        } catch (e: Exception) {
            Log.e(tag, "Error starting audio recording: ${e.message}", e)
            stopRecording()
        }
    }

    /**
     * Stops microphone recording.
     */
    fun stopRecording() {
        _isRecording.value = false
        _rmsAmplitude.value = 0f
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w(tag, "Error stopping AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
        }
    }

    /**
     * Plays streamed PCM audio bytes directly into the speaker via AudioTrack.
     */
    fun enqueueAudioForPlayback(pcmAudioBytes: ByteArray) {
        playbackQueue.offer(pcmAudioBytes)
        ensureAudioTrackPlaying()
    }

    private fun ensureAudioTrackPlaying() {
        if (playbackJob?.isActive == true) return

        playbackJob = scope.launch(Dispatchers.IO) {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE_IN_HZ,
                CHANNEL_CONFIG_OUT,
                AUDIO_FORMAT
            ).coerceAtLeast(BUFFER_CHUNK_SIZE * 4)

            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE_IN_HZ)
                            .setChannelMask(CHANNEL_CONFIG_OUT)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()
                _isPlaying.value = true

                while (isActive) {
                    val chunk = playbackQueue.poll()
                    if (chunk != null) {
                        audioTrack?.write(chunk, 0, chunk.size)
                    } else {
                        // Queue empty, wait slightly before checking or finishing
                        kotlinx.coroutines.delay(20)
                        if (playbackQueue.isEmpty()) {
                            break
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(tag, "Audio playback cancelled/interrupted.")
            } catch (e: Exception) {
                Log.e(tag, "Error in AudioTrack playback: ${e.message}", e)
            } finally {
                _isPlaying.value = false
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (e: Exception) {
                    Log.w(tag, "Error releasing audio track: ${e.message}")
                }
                audioTrack = null
            }
        }
    }

    /**
     * Immediately stops and purges any currently playing audio (Barge-in / Interruption).
     */
    fun stopPlayback() {
        playbackQueue.clear()
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null

        try {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.pause()
                    it.flush()
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w(tag, "Error clearing AudioTrack: ${e.message}")
        } finally {
            audioTrack = null
        }
    }

    private fun calculateRmsAmplitude(buffer: ByteArray, readBytes: Int): Float {
        if (readBytes <= 1) return 0f
        var sumSquares = 0.0
        val sampleCount = readBytes / 2

        for (i in 0 until sampleCount) {
            val sample = (buffer[i * 2].toInt() and 0xFF) or (buffer[i * 2 + 1].toInt() shl 8)
            val shortSample = sample.toShort()
            sumSquares += shortSample * shortSample
        }

        val rms = sqrt(sumSquares / sampleCount)
        // Normalize 0..32767 to 0f..1f
        return (rms / 32767.0).toFloat().coerceIn(0f, 1f)
    }

    fun release() {
        stopRecording()
        stopPlayback()
    }
}
