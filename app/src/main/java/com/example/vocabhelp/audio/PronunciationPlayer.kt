package com.example.vocabhelp.audio

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Pronounces a word. Plays the saved remote mp3 when one exists (words saved
 * while dictionaryapi.dev was the source), otherwise uses on-device
 * Text-to-Speech. Safe to call play() repeatedly; previous playback is stopped.
 * Caller must invoke release() when done (e.g., in DisposableEffect.onDispose).
 */
class PronunciationPlayer(context: Context) {
    private var player: MediaPlayer? = null

    private var ttsReady = false
    private var pendingSpeech: String? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            configureTts()
            pendingSpeech?.let { speak(it) }
        } else {
            Log.w(TAG, "TextToSpeech init failed: $status")
        }
        pendingSpeech = null
    }

    fun play(word: String, audioUrl: String? = null) {
        stopAll()
        if (!audioUrl.isNullOrBlank()) {
            // Fall back to TTS if the old audio link is dead.
            playUrl(audioUrl, onError = { speak(word) })
        } else {
            speak(word)
        }
    }

    fun release() {
        stopAll()
        tts.shutdown()
    }

    private fun speak(word: String) {
        if (word.isBlank()) return
        if (!ttsReady) {
            pendingSpeech = word
            return
        }
        tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun configureTts() {
        val result = tts.setLanguage(Locale.US)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH)
        }
        tts.setSpeechRate(0.9f)
    }

    private fun playUrl(url: String, onError: () -> Unit) {
        val resolved = if (url.startsWith("//")) "https:$url" else url
        try {
            player = MediaPlayer().apply {
                setDataSource(resolved)
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    it.release()
                    if (player === it) player = null
                }
                setOnErrorListener { mp, what, extra ->
                    Log.w(TAG, "MediaPlayer error: what=$what extra=$extra")
                    mp.release()
                    if (player === mp) player = null
                    onError()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play audio: $resolved", e)
            releasePlayer()
            onError()
        }
    }

    private fun stopAll() {
        pendingSpeech = null
        if (ttsReady) tts.stop()
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.let { p ->
            try {
                if (p.isPlaying) p.stop()
            } catch (_: IllegalStateException) {
                // ignore
            }
            p.release()
        }
        player = null
    }

    private companion object {
        const val TAG = "PronunciationPlayer"
        const val UTTERANCE_ID = "pronounce"
    }
}
