package org.rotationmusic.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.rotationmusic.app.R
import org.rotationmusic.app.core.Session
import org.rotationmusic.app.core.Wrapped
import java.util.Locale

/**
 * Puts the year's most played songs into Navidrome as a playlist, in order.
 *
 * The same button belongs in two places – the last story card and the foot of
 * the recap – so it is one composable used twice. What has been written is
 * remembered in the [Session], not here, so both places agree.
 *
 * Nothing is drawn where there is nothing to write: a friend's recap and the
 * demo both arrive without a playlist block.
 *
 * [onDark] is the story: white text on the gradient, and without the
 * explaining line or the second button, because that card is already full.
 */
@Composable
fun WrappedPlaylistButton(
    data: Wrapped,
    session: Session,
    onDark: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val info = data.playlist ?: return
    val scope = rememberCoroutineScope()
    var busy by remember(info.year) { mutableStateOf(false) }
    var failed by remember(info.year) { mutableStateOf(false) }
    val language = Locale.getDefault().language

    // What this run has written outranks what the recap was fetched with.
    val written = session.wrappedPlaylists[info.year] ?: info.takeIf { it.exists }

    val write = {
        if (!busy) {
            busy = true
            failed = false
            scope.launch {
                val result = session.perform { session.source!!.wrappedPlaylist(info.year, language) }
                busy = false
                if (result != null && result.ok) {
                    session.wrappedPlaylists[info.year] = result.playlist
                } else {
                    failed = true
                }
            }
        }
        Unit
    }

    val muted = if (onDark) Color.White.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier,
        horizontalAlignment = if (onDark) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (written != null && !busy) {
            Text(
                stringResource(R.string.wrapped_playlist_done)
                    .format(written.name, written.tracks),
                style = MaterialTheme.typography.bodySmall,
                textAlign = if (onDark) TextAlign.Center else TextAlign.Start,
                color = muted,
            )
            // Until the year is over the list still grows, so writing it again
            // has a point – but not on the story card.
            if (!onDark) {
                TextButton(onClick = write) {
                    Text(stringResource(R.string.wrapped_playlist_again))
                }
            }
        } else {
            if (!onDark) {
                Text(
                    stringResource(R.string.wrapped_playlist_hint)
                        .format(info.size, info.year),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            Button(onClick = write, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.wrapped_playlist_add).format(info.size))
                }
            }
        }
        if (failed) {
            Text(
                stringResource(R.string.playlist_failed),
                style = MaterialTheme.typography.bodySmall,
                textAlign = if (onDark) TextAlign.Center else TextAlign.Start,
                color = if (onDark) Color.White else MaterialTheme.colorScheme.error,
            )
        }
    }
}
