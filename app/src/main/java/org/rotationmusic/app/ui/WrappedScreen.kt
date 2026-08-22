package org.rotationmusic.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.rotationmusic.app.R
import org.rotationmusic.app.core.Format
import org.rotationmusic.app.core.Period
import org.rotationmusic.app.core.Session
import org.rotationmusic.app.core.Wrapped

@Composable
fun WrappedScreen(session: Session) {
    val context = LocalContext.current
    var year by remember { mutableStateOf<Int?>(null) }
    var data by remember { mutableStateOf<Wrapped?>(null) }
    var selectedMonth by remember { mutableStateOf<Int?>(null) }
    var card by remember { mutableStateOf<Bitmap?>(null) }
    var yearMenu by remember { mutableStateOf(false) }
    var theme by rememberSaveable { mutableStateOf(CardTheme.SUNSET) }
    var detail by remember { mutableStateOf<DetailTarget?>(null) }
    var storyOpen by remember { mutableStateOf(false) }
    val seasonOpen = session.me?.season?.open == true || session.settings.storyAllYear
    val viewing = session.viewing

    val labels = CardLabels(
        kicker = "${stringResource(R.string.wrapped_year)} ${data?.year ?: ""}",
        topArtists = stringResource(R.string.card_artists),
        topTracks = stringResource(R.string.card_tracks),
        minutes = stringResource(R.string.card_minutes),
        topGenre = stringResource(R.string.card_topgenre),
    )
    val playsTemplate = stringResource(R.string.plays_count)

    LaunchedEffect(year, viewing?.id) {
        data = session.perform { session.source!!.wrapped(year, viewing?.id) }
        selectedMonth = data?.topMonth?.month
        card = null
    }

    // Rendering happens once per year and colour, not on every redraw.
    LaunchedEffect(data?.year, data?.hasData, theme) {
        val wrapped = data
        if (wrapped != null && wrapped.hasData) {
            // The artwork has to be in hand before drawing: the card is
            // painted in one pass and cannot wait for a download.
            val cover = loadArtwork(context, session, wrapped.artists.firstOrNull()?.art)
            card = renderShareCard(
                wrapped, wrapped.user?.name ?: session.me?.user?.name.orEmpty(), cover,
                labels, theme)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val wrapped = data
        if (wrapped == null) {
            item { LoadingRow() }
        } else if (!wrapped.hasData) {
            item {
                Text(stringResource(R.string.wrapped_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            if (seasonOpen) {
                item {
                    Button(
                        onClick = { storyOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("▶  " + stringResource(R.string.story_start))
                    }
                }
            }

            item {
                Box {
                    TextButton(onClick = { yearMenu = true }) {
                        Text("${wrapped.year}")
                    }
                    DropdownMenu(yearMenu, onDismissRequest = { yearMenu = false }) {
                        wrapped.years.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("$option") },
                                onClick = { year = option; yearMenu = false },
                            )
                        }
                    }
                }
            }

            item { Hero(wrapped) }

            wrapped.artists.firstOrNull()?.let { artist ->
                item {
                    RotationCard(stringResource(R.string.wrapped_artist)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    detail = DetailTarget(
                                        "artist", artist.id, wrapped.year.toString())
                                },
                        ) {
                            Artwork(artist.art, artist.name, 72.dp, session, round = true)
                            Column {
                                Text(artist.name, fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold)
                                Text(playsTemplate.format(artist.plays),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.wrapped_devotion)
                                    .format(wrapped.devotion),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.weight(1f))
                            Text("›", style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                RotationCard(stringResource(R.string.wrapped_months)) {
                    MonthBars(wrapped.months, selectedMonth, { selectedMonth = it }) { count ->
                        playsTemplate.format(count)
                    }
                }
            }

            if (wrapped.tracks.isNotEmpty()) {
                item {
                    TopCard(stringResource(R.string.card_tracks), session,
                        wrapped.tracks.take(5).mapIndexed { index, track ->
                            TopItem(index + 1, track.title, track.artist, track.art,
                                track.plays, share(track.plays, wrapped.tracks.first().plays))
                        }) { index ->
                        detail = DetailTarget("track", wrapped.tracks[index].id,
                            wrapped.year.toString())
                    }
                }
            }
            if (wrapped.albums.isNotEmpty()) {
                item {
                    TopCard(stringResource(R.string.card_albums), session,
                        wrapped.albums.take(5).mapIndexed { index, album ->
                            TopItem(index + 1, album.name, album.artist.orEmpty(), album.art,
                                album.plays, share(album.plays, wrapped.albums.first().plays))
                        }) { index ->
                        detail = DetailTarget("album", wrapped.albums[index].id,
                            wrapped.year.toString())
                    }
                }
            }

            item {
                RotationCard(stringResource(R.string.wrapped_year)) {
                    wrapped.firstPlay?.let {
                        Fact(stringResource(R.string.wrapped_first),
                            "${it.title} — ${it.artist}")
                    }
                    Fact(stringResource(R.string.wrapped_streak),
                        stringResource(R.string.days_count).format(wrapped.streak))
                    wrapped.clock.peakHour?.let {
                        Fact(stringResource(R.string.wrapped_hour), Format.hour(it))
                    }
                    if (wrapped.discoveries.isNotEmpty()) {
                        Fact(stringResource(R.string.wrapped_new),
                            wrapped.discoveries.take(3).joinToString(", ") { it.name })
                    }
                }
            }

            // The picture last, with its colours and its button right there.
            card?.let { bitmap ->
                item {
                    RotationCard(stringResource(R.string.card_theme)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CardTheme.entries.forEach { option ->
                                Swatch(option, option == theme) { theme = option }
                            }
                        }
                        // What you see is what gets shared: the same bitmap.
                        Image(
                            bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp)),
                        )
                        Button(
                            onClick = { shareBitmap(context, bitmap, wrapped.year) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.wrapped_share))
                        }
                    }
                }
            }
        }
    }

    detail?.let { target ->
        DetailSheet(target, session) { detail = null }
    }

    if (storyOpen) {
        data?.let { wrapped ->
            StoryScreen(wrapped, session) { storyOpen = false }
        }
    }
}

/** One colour choice for the card: the gradient itself, as a circle. */
@Composable
private fun Swatch(theme: CardTheme, selected: Boolean, onSelect: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(theme.stops.map { Color(it) }))
            .then(
                if (selected) Modifier.border(3.dp, Color.White, CircleShape)
                else Modifier
            )
            .clickable(onClick = onSelect)
    )
}

@Composable
private fun Hero(data: Wrapped) {
    val playsTemplate = stringResource(R.string.plays_count)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Accent, Deep, AccentAlt)))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(playsTemplate.format(data.summary.plays),
            fontSize = 38.sp, lineHeight = 44.sp,
            fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text("${Format.duration(data.summary.seconds)} · " +
            stringResource(R.string.artists_count).format(data.summary.artists),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f))
        data.previous?.let { previous ->
            val delta = ((data.summary.plays - previous.plays) * 100f / previous.plays)
                .toInt()
            Text("${if (delta >= 0) "+" else ""}$delta% " +
                stringResource(R.string.wrapped_compared).format(previous.year),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
