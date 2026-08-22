package org.rotationmusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.rotationmusic.app.R
import org.rotationmusic.app.core.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FriendsScreen(session: Session, onView: (Person) -> Unit) {
    var lists by remember { mutableStateOf<FriendLists?>(null) }
    var comparing by remember { mutableStateOf<Person?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val requested = stringResource(R.string.friends_requested)
    val now = stringResource(R.string.friends_now)
    val hint = stringResource(R.string.friends_hint)
    val titleIncoming = stringResource(R.string.friends_incoming)
    val titleYours = stringResource(R.string.friends_yours)
    val titleOutgoing = stringResource(R.string.friends_outgoing)
    val titleSuggestions = stringResource(R.string.friends_suggestions)
    val noneYet = stringResource(R.string.friends_none)
    val noSuggestions = stringResource(R.string.friends_nosuggestions)

    suspend fun reload() {
        lists = session.perform { session.source!!.friends() }
    }

    LaunchedEffect(Unit) { reload() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val data = lists
            if (data == null) {
                item { LoadingRow() }
            } else {
                section(titleIncoming, data.incoming) { person ->
                    Button(onClick = {
                        scope.launch {
                            session.perform { session.source!!.addFriend(person.id) }
                            notice = now
                            reload()
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text(stringResource(R.string.friends_accept))
                    }
                }

                section(titleYours, data.friends, empty = noneYet) { person ->
                    TextButton(onClick = { onView(person) }) {
                        Text(stringResource(R.string.friends_view), maxLines = 1)
                    }
                    TextButton(onClick = { comparing = person }) {
                        Text(stringResource(R.string.friends_compare), maxLines = 1)
                    }
                    TextButton(onClick = {
                        scope.launch {
                            session.perform {
                                session.source!!.removeFriend(person.id, false)
                            }
                            reload()
                        }
                    }) { Text(stringResource(R.string.friends_remove), maxLines = 1) }
                }

                section(titleOutgoing, data.outgoing) { person ->
                    TextButton(onClick = {
                        scope.launch {
                            session.perform { session.source!!.removeFriend(person.id, true) }
                            reload()
                        }
                    }) { Text(stringResource(R.string.friends_withdraw)) }
                }

                section(titleSuggestions, data.suggestions, empty = noSuggestions) { person ->
                    OutlinedButton(onClick = {
                        scope.launch {
                            val mutual = session.perform {
                                session.source!!.addFriend(person.id)
                            } ?: false
                            notice = if (mutual) now else requested
                            reload()
                        }
                    }) { Text(stringResource(R.string.friends_add)) }
                }
            }
        }

        notice?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(2600)
                notice = null
            }
            Surface(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(message, Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    comparing?.let { person ->
        CompareSheet(session, person) { comparing = null }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    people: List<Person>,
    empty: String? = null,
    trailing: @Composable (Person) -> Unit,
) {
    if (people.isEmpty() && empty == null) return
    item {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (people.isEmpty()) {
        item {
            Text(empty!!, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        items(people.size) { index ->
            val person = people[index]
            // Name above, buttons below: three of them beside a name turn
            // into vertical letters on a narrow phone.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(person.name.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold)
                    }
                    Text(person.name, Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    trailing(person)
                }
            }
        }
    }
}

/** Two people side by side, over a period of its own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareSheet(session: Session, friend: Person, onDismiss: () -> Unit) {
    var period by remember { mutableStateOf(Period.MONTH) }
    var data by remember { mutableStateOf<Comparison?>(null) }

    LaunchedEffect(period) {
        data = session.perform { session.source!!.compare(friend.id, period) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(friend.name, style = MaterialTheme.typography.titleMedium)
            PeriodChips(period) { period = it }

            val comparison = data
            if (comparison == null) {
                LoadingRow()
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Side(comparison.me, Accent, Modifier.weight(1f))
                    Side(comparison.them, AccentAlt, Modifier.weight(1f))
                }
                if (comparison.shared.isEmpty()) {
                    Text(stringResource(R.string.empty_overlap),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    RotationCard(stringResource(R.string.card_shared)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Legend(comparison.me.user.name, Accent)
                            Legend(comparison.them.user.name, AccentAlt)
                        }
                        comparison.shared.forEach { entry ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(entry.name, style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(90.dp))
                                Row(
                                    Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(CircleShape)
                                ) {
                                    val total = (entry.mine + entry.theirs).coerceAtLeast(1)
                                    Box(
                                        Modifier
                                            .fillMaxHeight()
                                            .weight(entry.mine.toFloat() / total)
                                            .background(Accent)
                                    )
                                    Box(
                                        Modifier
                                            .fillMaxHeight()
                                            .weight(entry.theirs.toFloat() / total)
                                            .background(AccentAlt)
                                    )
                                }
                                Text("${entry.mine} · ${entry.theirs}",
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

@Composable
private fun Side(side: ComparisonSide, colour: androidx.compose.ui.graphics.Color,
                 modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(side.user.name, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(Format.number(side.summary.plays), fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold, color = colour)
        Text(stringResource(R.string.stat_plays).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Format.duration(side.summary.seconds),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.artists_count).format(side.summary.artists),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Legend(name: String, colour: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(colour))
        Text(name, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
