package org.rotationmusic.app.ui

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Thirty seconds of a song, from a third of the way in – which is where a
 * song usually says what it is.
 *
 * Deliberately not a full player: Rotation counts music, it does not play it.
 * This is a taste, so a name in a list can be recognised again.
 */
class TrackPreview {

    var playing by mutableStateOf(false)
        private set

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var url: String? = null

    /** [file] already holds the middle of the song – see [Snippet]. */
    fun play(trackId: String, file: java.io.File?) {
        if (playing && trackId == url) { stop(); return }
        stop()
        if (file == null) return
        url = trackId
        runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { media ->
                    media.setVolume(0f, 0f)
                    media.start()
                    fade(media, 0.9f)
                    handler.postDelayed({ stop() }, 30_000)
                }
                setOnCompletionListener { stop() }
                prepareAsync()
                player = this
                playing = true
            }
        }.onFailure { stop() }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.release() }
        player = null
        playing = false
    }

    private fun fade(media: MediaPlayer, target: Float) {
        Thread {
            var level = 0f
            while (level < target) {
                level += target / 20f
                runCatching { media.setVolume(level, level) }
                Thread.sleep(30)
            }
        }.start()
    }
}
