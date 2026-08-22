package org.rotationmusic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.Factory
import org.rotationmusic.app.core.Season
import org.rotationmusic.app.core.SeasonReminder
import org.rotationmusic.app.core.Session
import org.rotationmusic.app.ui.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val session = ViewModelProvider(this, object : Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                Session(applicationContext) as T
        })[Session::class.java]

        // The reminder is on by default; this asks once and arms 1 December.
        SeasonReminder.refresh(this)

        setContent {
            // The palette is read here so the whole tree redraws when it
            // changes; SettingsScreen writes it.
            RotationTheme(accent = session.accent) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    when (session.stage) {
                        Session.Stage.LOADING -> Box(
                            Modifier.fillMaxSize(), Alignment.Center) { RecordMark(64.dp) }
                        Session.Stage.SERVER -> ServerSetupScreen(session)
                        Session.Stage.LOGIN -> LoginScreen(session)
                        Session.Stage.READY -> MainScreen(session)
                    }
                }
            }
        }
    }
}
