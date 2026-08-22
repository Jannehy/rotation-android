package org.rotationmusic.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.rotationmusic.app.R
import org.rotationmusic.app.core.Session

/** The app's mark: a record, drawn rather than shipped as an image. */
@Composable
fun RecordMark(size: Dp) {
    Canvas(Modifier.size(size)) {
        val radius = this.size.minDimension / 2
        drawCircle(Color(0xFF12121A), radius)
        listOf(0.82f, 0.66f, 0.50f).forEach { ratio ->
            drawCircle(Color(0xFF3A3A4A), radius * ratio, style = Stroke(width = radius * 0.05f))
        }
        drawCircle(Accent, radius * 0.30f)
        drawCircle(Color(0xFF0A0A0F), radius * 0.08f)
    }
}

@Composable
fun ServerSetupScreen(session: Session) {
    var address by remember { mutableStateOf(session.serverAddress) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RecordMark(76.dp)
        Spacer(Modifier.height(14.dp))
        Text("Rotation", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.setup_tagline),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)

        Spacer(Modifier.height(26.dp))
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            singleLine = true,
            label = { Text(stringResource(R.string.setup_address)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { session.connect(address) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { session.connect(address) },
            enabled = address.isNotBlank() && !session.isWorking,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (session.isWorking) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.setup_connect), fontWeight = FontWeight.SemiBold)
        }
        session.errorRes?.let { error ->
            Spacer(Modifier.height(10.dp))
            Text(stringResource(error), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        TextButton(onClick = { session.startDemo() }) {
            Text(stringResource(R.string.setup_demo))
        }

        Spacer(Modifier.height(30.dp))
        Text(stringResource(R.string.setup_footnote),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}

@Composable
fun LoginScreen(session: Session) {
    var username by remember { mutableStateOf(session.username.orEmpty()) }
    var password by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RecordMark(64.dp)
        Spacer(Modifier.height(12.dp))
        Text("Rotation", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.login_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            singleLine = true,
            label = { Text(stringResource(R.string.login_user)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            singleLine = true,
            label = { Text(stringResource(R.string.login_pass)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { session.signIn(username, password) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { session.signIn(username, password) },
            enabled = username.isNotBlank() && password.isNotBlank() && !session.isWorking,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (session.isWorking) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.login_go), fontWeight = FontWeight.SemiBold)
        }
        session.errorRes?.let { error ->
            Spacer(Modifier.height(10.dp))
            Text(stringResource(error), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { session.forgetServer() }) {
                Text(stringResource(R.string.login_other_server))
            }
            TextButton(onClick = { session.startDemo() }) {
                Text(stringResource(R.string.setup_demo))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(session.serverAddress,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
