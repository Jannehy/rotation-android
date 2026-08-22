package org.rotationmusic.app.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * A piece out of the middle of a song, fetched into a file.
 *
 * MediaPlayer can stream over HTTP, but not through Rotation's session: it
 * keeps its own network stack and hands over no cookies, so every request
 * came back unauthorised. Fetching the bytes here – with the same client that
 * already carries the session – and playing a local file removes that whole
 * class of problem, and it lets us take only the slice we need: an MP3 is a
 * chain of frames, so a cut out of the middle plays on its own.
 */
object Snippet {

    /** Roughly a minute at 320 kbit/s – enough for any snippet we play. */
    private const val BYTES = 2_400_000L

    suspend fun fetch(context: Context, api: Api, trackId: String,
                      share: Double = 0.32): File? = withContext(Dispatchers.IO) {
        val url = api.streamUrl(trackId) ?: return@withContext null
        val client = OkHttpClient()
        val cookie = api.cookieHeader()

        fun request(range: String) = Request.Builder()
            .url(url)
            .header("Range", range)
            .apply { if (cookie.isNotEmpty()) header("Cookie", cookie) }
            .build()

        runCatching {
            // One byte first, only to learn how long the song is.
            val total = client.newCall(request("bytes=0-0")).execute().use { probe ->
                probe.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
            } ?: return@withContext null

            val start = (total * share).toLong().coerceAtMost((total - BYTES).coerceAtLeast(0))
            val end = (start + BYTES).coerceAtMost(total - 1)
            val target = File(context.cacheDir, "snippet-$trackId.mp3")
            client.newCall(request("bytes=$start-$end")).execute().use { answer ->
                if (!answer.isSuccessful) return@withContext null
                answer.body?.byteStream()?.use { source ->
                    target.outputStream().use { sink -> source.copyTo(sink) }
                }
            }
            target
        }.getOrNull()
    }

    /** Nothing here is worth keeping between sessions. */
    fun clear(context: Context) {
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("snippet-") }
            ?.forEach { runCatching { it.delete() } }
    }
}
