package com.example.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val tag = "AudioPlayer"

    fun playAudioBytes(audioBytes: ByteArray, onCompletion: (() -> Unit)? = null) {
        try {
            stop()
            val tempFile = File.createTempFile("jarvis_tts_", ".mp3", context.cacheDir).apply {
                deleteOnExit()
            }
            FileOutputStream(tempFile).use { fos ->
                fos.write(audioBytes)
                fos.flush()
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnPreparedListener { start() }
                setOnCompletionListener {
                    tempFile.delete()
                    onCompletion?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(tag, "MediaPlayer error: what=$what extra=$extra")
                    tempFile.delete()
                    onCompletion?.invoke()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to play audio bytes: ${e.message}", e)
            onCompletion?.invoke()
        }
    }

    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w(tag, "Error stopping mediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }

    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true
}
