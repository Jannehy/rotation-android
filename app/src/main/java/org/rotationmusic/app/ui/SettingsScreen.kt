package org.rotationmusic.app.ui

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
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import org.rotationmusic.app.core.SeasonReminder
import org.rotationmusic.app.R
import org.rotationmusic.app.core.Format
import org.rotationmusic.app.core.Session

@Composable
fun SettingsScreen(session: Session) {
    var discoverable by remember { mutableStateOf(session.me?.discoverable ?: true) }
    var accent by remember { mutableStateOf(session.accent) }
    var allYear by remember { mutableStateOf(session.settings.storyAllYear) }
    var remind by remember { mutableStateOf(session.settings.seasonReminder) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (session.isDemo) {
            item {
                RotationCard(stringResource(R.string.demo_banner)) {
                    Text(stringResource(R.string.demo_explain),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { session.signOut() },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text(stringResource(R.string.demo_connect))
                    }
                }
            }
        } else {
            item {
                RotationCard(stringResource(R.string.card_account)) {
                    Fact(stringResource(R.string.settings_user),
                        session.me?.user?.name.orEmpty())
                    session.me?.bounds?.takeIf { it.firstPlay > 0 }?.let { bounds ->
                        Fact(stringResource(R.string.settings_since),
                            Format.date(bounds.firstPlay))
                        Fact(stringResource(R.string.settings_total),
                            Format.number(bounds.historyPlays))
                    }
                    Fact(stringResource(R.string.settings_server), session.serverAddress)
                }
            }
            item {
                RotationCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_discoverable),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = discoverable,
                            onCheckedChange = { value ->
                                discoverable = value
                                scope.launch {
                                    session.perform {
                                        session.source!!.setDiscoverable(value)
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                        )
                    }
                    Text(stringResource(R.string.settings_discoverable_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                RotationCard(stringResource(R.string.settings_colour)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Palette.entries.forEach { option ->
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(
                                        listOf(option.accent, option.second)))
                                    .then(
                                        if (accent == option.id)
                                            Modifier.border(3.dp,
                                                MaterialTheme.colorScheme.onSurface,
                                                CircleShape)
                                        else Modifier)
                                    .clickable {
                                        accent = option.id
                                        session.chooseAccent(option.id)
                                    },
                            )
                        }
                    }
                }
            }
            item {
                RotationCard(stringResource(R.string.wrapped_year)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_season),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = allYear,
                            onCheckedChange = {
                                allYear = it
                                session.settings.storyAllYear = it
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                        )
                    }
                    Text(stringResource(R.string.settings_season_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_notify),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = remind,
                            onCheckedChange = {
                                remind = it
                                session.settings.seasonReminder = it
                                SeasonReminder.refresh(context)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                        )
                    }
                    Text(stringResource(R.string.settings_notify_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                RotationCard {
                    TextButton(onClick = { session.signOut() }) {
                        Text(stringResource(R.string.settings_signout),
                            color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { session.forgetServer() }) {
                        Text(stringResource(R.string.settings_other_server))
                    }
                }
            }
        }

        item {
            RotationCard(stringResource(R.string.card_about)) {
                Fact("Rotation", session.me?.version.orEmpty())
                Text(stringResource(R.string.settings_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
