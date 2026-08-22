package org.rotationmusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.rotationmusic.app.R
import org.rotationmusic.app.core.Session

private enum class Tab(val label: Int, val icon: ImageVector) {
    OVERVIEW(R.string.nav_overview, Icons.Filled.BarChart),
    HISTORY(R.string.nav_history, Icons.Filled.History),
    WRAPPED(R.string.nav_wrapped, Icons.Filled.Star),
    FRIENDS(R.string.nav_friends, Icons.Filled.People),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun MainScreen(session: Session) {
    var tab by rememberSaveable { mutableStateOf(Tab.OVERVIEW) }
    val tabs = remember(session.isDemo) {
        // The demo has nobody to be friends with.
        if (session.isDemo) Tab.entries.filter { it != Tab.FRIENDS } else Tab.entries
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = {
                            Text(stringResource(entry.label), maxLines = 1, softWrap = false)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = Accent,
                        ),
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            session.viewing?.let { person ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.viewing).format(person.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { session.viewing = null }) {
                        Text(stringResource(R.string.viewing_back),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            when (tab) {
                Tab.OVERVIEW -> OverviewScreen(session)
                Tab.HISTORY -> HistoryScreen(session)
                Tab.WRAPPED -> WrappedScreen(session)
                Tab.FRIENDS -> FriendsScreen(session) { person ->
                    // Friends is about my own connections, so looking at
                    // someone else's numbers moves the app to the overview.
                    session.viewing = person
                    tab = Tab.OVERVIEW
                }
                Tab.SETTINGS -> SettingsScreen(session)
            }
        }
    }
}
