package org.rotationmusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import org.rotationmusic.app.R
import org.rotationmusic.app.core.*

/**
 * What to look at: a track, an artist or an album – and over which period.
 *
 * The period travels with the target so the numbers match the list the name
 * was tapped in. The recap screen passes its year, everything else the period
 * its chips are set to.
 */
data class DetailTarget(val kind: String, val id: String, val range: String)

/**
 * The sheet every name in a list opens. Inside it names keep working, so an
 * artist leads to a track and a track back to its album; [stack] remembers the
 * way back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(target: DetailTarget, session: Session, onDismiss: () -> Unit) {
    val stack = remember(target) { mutableStateListOf(target) }
    val current = stack.last()
    var data by remember { mutableStateOf<ItemDetail?>(null) }
    var missing by remember { mutableStateOf(false) }

    LaunchedEffect(current) {
        data = null
        missing = false
        val loaded = session.perform {
            session.source!!.detail(current.kind, current.id, current.range,
                session.viewing?.id)
        }
        data = loaded
        missing = loaded == null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (stack.size > 1) {
                TextButton(onClick = { stack.removeAt(stack.lastIndex) }) {
                    Text("‹ ${stringResource(R.string.detail_close)}")
                }
            }
            val detail = data
            when {
                detail != null -> DetailBody(detail, session) { next -> stack.add(next) }
                missing -> Text(
                    stringResource(R.string.detail_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LoadingRow()
            }
        }
    }
}

@Composable
private fun DetailBody(
    data: ItemDetail,
    session: Session,
    onOpen: (DetailTarget) -> Unit,
) {
    val range = data.range.key
    Header(data, session)
    Tiles(data)
    if (data.months.isNotEmpty()) {
        RotationCard(stringResource(R.string.detail_months) + " · " +
            stringResource(R.string.range_all)) {
            MonthMini(padMonths(data.months, 12))
        }
    }
    if (data.hours.any { it > 0 }) {
        RotationCard(stringResource(R.string.detail_hours) + " · " +
            stringResource(R.string.range_all)) {
            HourMini(data.hours)
        }
    }
    if (data.topTracks.isNotEmpty()) {
        TopCard(stringResource(R.string.card_tracks), session,
            data.topTracks.mapIndexed { index, track ->
                TopItem(index + 1, track.title, track.artist, track.art, track.plays,
                    share(track.plays, data.topTracks.first().plays))
            }) { index -> onOpen(DetailTarget("track", data.topTracks[index].id, range)) }
    }
    if (data.topAlbums.isNotEmpty()) {
        TopCard(stringResource(R.string.card_albums), session,
            data.topAlbums.mapIndexed { index, album ->
                TopItem(index + 1, album.name, album.artist.orEmpty(), album.art, album.plays,
                    share(album.plays, data.topAlbums.first().plays))
            }) { index -> onOpen(DetailTarget("album", data.topAlbums[index].id, range)) }
    }
}

@Composable
private fun Header(data: ItemDetail, session: Session) {
    val preview = remember { TrackPreview() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DisposableEffect(data.id) { onDispose { preview.stop() } }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Artwork(data.art, data.displayName, 84.dp, session, round = data.kind == "artist")
            // A song can be listened to, not only counted. The badge is
            // always there: on a touch screen nothing reveals itself.
            if (data.kind == "track") {
                Box(
                    Modifier
                        .offset(x = 5.dp, y = 5.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Accent)
                        .clickable {
                            if (preview.playing) preview.stop()
                            else scope.launch {
                                val api = session.source as? Api ?: return@launch
                                preview.play(data.id, Snippet.fetch(context, api, data.id, 0.3))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (preview.playing) "❚❚" else "▶",
                         color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(data.displayName, fontSize = 20.sp, lineHeight = 24.sp,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (data.subtitle.isNotEmpty()) {
                Text(data.subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (data.kind == "artist") {
                Text(
                    stringResource(R.string.tracks_count).format(data.tracks) + " · " +
                        stringResource(R.string.albums_count).format(data.albums),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(periodName(data.range.key), accent = false)
                if (data.rank > 0) {
                    Chip("${stringResource(R.string.detail_rank)} ${data.rank}", accent = true)
                }
            }
        }
    }
}

/** "30d" -> "30 days". A plain year is already its own label. */
@Composable
private fun periodName(key: String): String =
    Period.entries.firstOrNull { it.key == key }?.let { stringResource(it.label) } ?: key

@Composable
private fun Chip(text: String, accent: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
        color = if (accent) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (accent) Accent else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun Tiles(data: ItemDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Format.number(data.plays), stringResource(R.string.stat_plays),
                accent = true, modifier = Modifier.weight(1f))
            StatTile(Format.duration(data.seconds), stringResource(R.string.stat_time),
                modifier = Modifier.weight(1f))
        }
        if (data.firstPlay > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Format.date(data.firstPlay),
                    stringResource(R.string.detail_first), modifier = Modifier.weight(1f))
                StatTile(Format.ago(data.lastPlay).toString(),
                    stringResource(R.string.detail_last), modifier = Modifier.weight(1f))
            }
        }
        if (data.kind == "album" && data.trackTotal > 0) {
            StatTile("${data.tracks}/${data.trackTotal}",
                stringResource(R.string.detail_heard), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MonthMini(months: List<MonthPoint>) {
    val peak = (months.maxOfOrNull { it.plays } ?: 1).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(70.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEach { point ->
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 3.dp)
                    .fillMaxHeight(point.plays.toFloat() / peak)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(Accent.copy(alpha = if (point.plays > 0) 0.85f else 0.2f))
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(months.firstOrNull()?.month.orEmpty(),
            style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(months.lastOrNull()?.month.orEmpty(),
            style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HourMini(hours: List<Int>) {
    val peak = (hours.maxOrNull() ?: 1).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(46.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        hours.forEach { value ->
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 2.dp)
                    .fillMaxHeight(value.toFloat() / peak)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(AccentAlt.copy(alpha = if (value > 0) 0.85f else 0.25f))
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(0, 6, 12, 18).forEach {
            Text("$it", style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Fills the gaps between the first and last month, then keeps extending
 * backwards – two plays in two months would draw two slabs otherwise.
 */
fun padMonths(months: List<MonthPoint>, minimum: Int): List<MonthPoint> {
    if (months.isEmpty()) return emptyList()
    fun parts(key: String): Pair<Int, Int> {
        val pieces = key.split("-")
        return (pieces.getOrNull(0)?.toIntOrNull() ?: 0) to
            (pieces.getOrNull(1)?.toIntOrNull() ?: 1)
    }
    fun key(year: Int, month: Int) = "%04d-%02d".format(year, month)

    val counts = months.associate { it.month to it.plays }
    var (year, month) = parts(months.first().month)
    val (endYear, endMonth) = parts(months.last().month)
    val filled = mutableListOf<MonthPoint>()
    while (year < endYear || (year == endYear && month <= endMonth)) {
        val name = key(year, month)
        filled.add(MonthPoint(name, counts[name] ?: 0))
        month += 1
        if (month > 12) { month = 1; year += 1 }
    }
    var (headYear, headMonth) = parts(months.first().month)
    while (filled.size < minimum) {
        headMonth -= 1
        if (headMonth < 1) { headMonth = 12; headYear -= 1 }
        filled.add(0, MonthPoint(key(headYear, headMonth), 0))
    }
    return filled
}
