package org.rotationmusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale
import org.rotationmusic.app.R
import org.rotationmusic.app.core.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewScreen(session: Session) {
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    var data by remember { mutableStateOf<Overview?>(null) }
    var detail by remember { mutableStateOf<DetailTarget?>(null) }
    var playlists by remember { mutableStateOf<List<ManagedPlaylist>>(emptyList()) }
    var writing by remember { mutableStateOf(false) }
    var storyYear by remember { mutableStateOf<Wrapped?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val viewing = session.viewing
    val managed = playlists.firstOrNull { it.range == period.key }
    val canManage = !session.isDemo && viewing == null
    // Read once here: the chart callbacks run outside a composable scope.
    val playsTemplate = stringResource(R.string.plays_count)
    val playlistDone = stringResource(R.string.playlist_done)
    val playlistDropped = stringResource(R.string.playlist_dropped)
    val playlistFailed = stringResource(R.string.playlist_failed)

    LaunchedEffect(period, viewing?.id) {
        data = session.perform { session.source!!.overview(period, viewing?.id) }
        playlists = if (canManage) {
            runCatching { session.source!!.playlists() }.getOrDefault(emptyList())
        } else emptyList()
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Once a year the recap announces itself here.
        session.me?.season?.takeIf { it.open }?.let { season ->
            item {
                SeasonBanner(season.year) {
                    storyYear = session.perform { session.source!!.wrapped(season.year, null) }
                }
            }
        }
        item { PeriodChips(period) { period = it } }

        val overview = data
        if (overview == null) {
            item { LoadingRow() }
        } else {
            item { Headline(overview) }
            item {
                RotationCard(stringResource(R.string.card_timeline)) {
                    TimelineChart(overview.timeline) { count -> playsTemplate.format(count) }
                }
            }
            item {
                RotationCard(stringResource(R.string.card_clock)) {
                    RecordClock(overview.clock) { count -> playsTemplate.format(count) }
                }
            }
            item {
                TopCard(stringResource(R.string.card_artists), session,
                    overview.artists.take(10).mapIndexed { index, artist ->
                        TopItem(index + 1, artist.name,
                            stringResource(R.string.tracks_count).format(artist.tracks),
                            artist.art, artist.plays,
                            share(artist.plays, overview.artists.firstOrNull()?.plays), true)
                    }) { index ->
                    detail = DetailTarget("artist", overview.artists[index].id, period.key)
                }
            }
            item {
                TopCard(
                    stringResource(R.string.card_tracks), session,
                    overview.tracks.take(10).mapIndexed { index, track ->
                        TopItem(index + 1, track.title, track.artist, track.art, track.plays,
                            share(track.plays, overview.tracks.firstOrNull()?.plays))
                    },
                    action = if (!canManage) null else CardAction(
                        title = stringResource(
                            if (managed == null) R.string.playlist_make
                            else R.string.playlist_update),
                        busy = writing,
                        secondary = if (managed == null) null
                                    else stringResource(R.string.playlist_drop),
                        onRun = {
                            scope.launch {
                                writing = true
                                val written = session.perform {
                                    session.source!!.writePlaylist(
                                        period.key, Locale.getDefault().language)
                                }
                                writing = false
                                notice = written?.let {
                                    playlistDone.format(it.playlist.name, it.tracks)
                                } ?: playlistFailed
                                playlists = runCatching { session.source!!.playlists() }
                                    .getOrDefault(emptyList())
                            }
                        },
                        onSecondary = {
                            scope.launch {
                                session.perform { session.source!!.dropPlaylist(period.key) }
                                notice = playlistDropped
                                playlists = runCatching { session.source!!.playlists() }
                                    .getOrDefault(emptyList())
                            }
                        },
                    ),
                ) { index ->
                    detail = DetailTarget("track", overview.tracks[index].id, period.key)
                }
            }
            item {
                TopCard(stringResource(R.string.card_albums), session,
                    overview.albums.take(10).mapIndexed { index, album ->
                        TopItem(index + 1, album.name, album.artist.orEmpty(), album.art,
                            album.plays,
                            share(album.plays, overview.albums.firstOrNull()?.plays))
                    }) { index ->
                    detail = DetailTarget("album", overview.albums[index].id, period.key)
                }
            }
            if (overview.genres.isNotEmpty()) {
                item {
                    RotationCard(stringResource(R.string.card_genres)) {
                        overview.genres.forEach { genre ->
                            GenreRow(genre, share(genre.plays, overview.genres.first().plays))
                        }
                    }
                }
            }
            if (overview.discoveries.isNotEmpty()) {
                item {
                    RotationCard(stringResource(R.string.card_discoveries)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            overview.discoveries.forEach { discovery ->
                                Row(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            detail = DetailTarget(
                                                "artist", discovery.id, period.key)
                                        }
                                        .padding(horizontal = 11.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(discovery.name,
                                        style = MaterialTheme.typography.bodySmall)
                                    Text("${discovery.plays}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { target ->
        DetailSheet(target, session) { detail = null }
    }

    storyYear?.let { wrapped ->
        StoryScreen(wrapped, session) { storyYear = null }
    }

    notice?.let { text ->
        AlertDialog(
            onDismissRequest = { notice = null },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } },
            text = { Text(text) },
        )
    }
}

/** The card that brings the recap forward once a year. */
@Composable
fun SeasonBanner(year: Int, open: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Accent, Deep, AccentAlt)))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.season_title).format(year),
                 fontSize = 20.sp, lineHeight = 24.sp,
                 fontWeight = FontWeight.Black, color = Color.White)
            Text(stringResource(R.string.season_sub),
                 style = MaterialTheme.typography.bodySmall,
                 color = Color.White.copy(alpha = 0.85f))
        }
        Button(
            onClick = { scope.launch { loading = true; open(); loading = false } },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White, contentColor = Color.Black),
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.Black)
            else Text(stringResource(R.string.season_open))
        }
    }
}

data class TopItem(
    val rank: Int, val title: String, val subtitle: String, val art: String?,
    val plays: Int, val share: Float, val round: Boolean = false,
)

/** Something a card offers next to its title – the playlist button uses it. */
data class CardAction(
    val title: String,
    val busy: Boolean = false,
    val secondary: String? = null,
    val onRun: () -> Unit,
    val onSecondary: (() -> Unit)? = null,
)

@Composable
fun TopCard(
    title: String,
    session: Session,
    items: List<TopItem>,
    action: CardAction? = null,
    onClick: ((Int) -> Unit)? = null,
) {
    RotationCard(title, action = action) {
        if (items.isEmpty()) {
            Text(stringResource(R.string.empty_period),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            items.forEachIndexed { index, item ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(if (onClick == null) Modifier
                              else Modifier.clickable { onClick(index) })
                ) {
                    TopRow(item.rank, item.title, item.subtitle, item.art, item.plays,
                        item.share, session, item.round)
                }
            }
        }
    }
}

@Composable
private fun Headline(data: Overview) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Format.number(data.summary.plays),
                stringResource(R.string.stat_plays), accent = true,
                modifier = Modifier.weight(1f))
            StatTile(Format.duration(data.summary.seconds),
                stringResource(R.string.stat_time), modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Format.number(data.summary.artists),
                stringResource(R.string.stat_artists), modifier = Modifier.weight(1f))
            StatTile(Format.number(data.summary.tracks),
                stringResource(R.string.stat_tracks), modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Format.number(data.summary.activeDays),
                stringResource(R.string.stat_active), modifier = Modifier.weight(1f))
            StatTile("${data.streaks.current}",
                stringResource(R.string.stat_streak), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun GenreRow(genre: GenreEntry, share: Float) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(genre.name, style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(96.dp))
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(share.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Accent)
            )
        }
        Text(Format.number(genre.plays), style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp))
    }
}

@Composable
fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Accent)
    }
}

fun share(value: Int, top: Int?): Float =
    if (top == null || top <= 0) 0f else value.toFloat() / top
