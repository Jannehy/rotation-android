package org.rotationmusic.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.rotationmusic.app.R
import org.rotationmusic.app.core.*

@Composable
fun HistoryScreen(session: Session) {
    var overview by remember { mutableStateOf<Overview?>(null) }
    var plays by remember { mutableStateOf<List<PlayEntry>>(emptyList()) }
    var detail by remember { mutableStateOf<DetailTarget?>(null) }
    val viewing = session.viewing

    LaunchedEffect(viewing?.id) {
        overview = session.perform { session.source!!.overview(Period.ALL, viewing?.id) }
        plays = session.perform { session.source!!.recent(60, viewing?.id) }?.plays.orEmpty()
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        overview?.let { data ->
            item {
                RotationCard(stringResource(R.string.card_heatmap)) {
                    Heatmap(data.clock.matrix)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("${data.streaks.current}",
                        stringResource(R.string.stat_current), accent = true,
                        modifier = Modifier.weight(1f))
                    StatTile("${data.streaks.longest}",
                        stringResource(R.string.stat_longest),
                        modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            RotationCard(stringResource(R.string.card_recent)) {
                if (plays.isEmpty()) {
                    Text(stringResource(R.string.empty_period),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    plays.forEach { play ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                // The history is the whole story, so the page
                                // behind a play shows all of it too.
                                .clickable {
                                    detail = DetailTarget("track", play.id, Period.ALL.key)
                                },
                        ) {
                            Artwork(play.art, play.artist, 42.dp, session)
                            Column(Modifier.weight(1f)) {
                                Text(play.title, style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(play.artist, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(Format.ago(play.playedAt).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    detail?.let { target ->
        DetailSheet(target, session) { detail = null }
    }
}
