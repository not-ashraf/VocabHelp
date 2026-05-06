package com.example.vocabhelp.audio

import android.media.MediaPlayer
import android.util.Log

/**
 * Lightweight wrapper around MediaPlayer for one-shot remote mp3 playback.
 * Safe to call play() repeatedly; previous playback is stopped.
 * Caller must invoke release() when done (e.g., in DisposableEffect.onDispose).
 */
class PronunciationPlayer {
    private var player: MediaPlayer? = null

    fun play(url: String) {
        if (url.isBlank()) return
        release()
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
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play audio: $resolved", e)
            release()
        }
    }

    fun release() {
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
    }
}
