package org.rotationmusic.app.ui

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.rotationmusic.app.R
import org.rotationmusic.app.core.Api
import org.rotationmusic.app.core.Format
import org.rotationmusic.app.core.Snippet
import org.rotationmusic.app.core.Session
import org.rotationmusic.app.core.Wrapped
import kotlin.math.sin

private const val HOLD_MS = 7_000L
private const val QUESTION_MS = 14_000L
private const val ANSWERED_MS = 1_600L
private const val TAP_MS = 220L
private const val COOLDOWN_MS = 500L

/**
 * The year as a story: full screen, one card at a time, running by itself.
 *
 * Hold to pause, tap left or right to step. Two cards ask before they tell,
 * because a number lands differently when you have just guessed at it. Four
 * of the cards bring a song with them; the others let it run on.
 */
@Composable
fun StoryScreen(data: Wrapped, session: Session, onClose: () -> Unit) {
    val context = LocalContext.current
    val cards = remember(data) { storyCards(data) }
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(session.settings.storyMuted) }
    val answers = remember { mutableStateMapOf<Int, Boolean>() }
    var confetti by remember { mutableStateOf(0) }
    val shake = remember { Animatable(0f) }
    var lastStep by remember { mutableLongStateOf(0L) }
    val progress = remember { Animatable(0f) }
    val player = remember { StoryPlayer() }

    val card = cards.getOrNull(index)

    fun step(direction: Int) {
        val now = System.currentTimeMillis()
        // Two taps that close together are one thumb, not a wish to skip two.
        if (now - lastStep < COOLDOWN_MS) return
        lastStep = now
        val next = index + direction
        if (next >= cards.size) onClose() else index = next.coerceAtLeast(0)
    }

    DisposableEffect(Unit) { onDispose { player.stop() } }

    LaunchedEffect(index, muted) {
        val current = cards.getOrNull(index) ?: return@LaunchedEffect
        val api = session.source as? Api
        val track = current.music
        if (api != null && track != null) {
            val file = Snippet.fetch(context, api, track.id)
            player.play(track.id, file, muted)
        }
    }

    // Counting up while running and simply not counting while held, so a
    // pause of any length leaves the card exactly where it was.
    LaunchedEffect(index) {
        progress.snapTo(0f)
        val current = cards.getOrNull(index) ?: return@LaunchedEffect
        if (current.isFinale) { progress.snapTo(1f); return@LaunchedEffect }
        var span = if (current.isQuestion) QUESTION_MS else HOLD_MS
        var elapsed = 0L
        val tick = 33L
        while (elapsed < span) {
            delay(tick)
            if (!isActive) return@LaunchedEffect
            if (paused) continue
            if (current.isQuestion && answers[index] != null && span > ANSWERED_MS) {
                span = minOf(span, elapsed + ANSWERED_MS)
            }
            elapsed += tick
            progress.snapTo((elapsed.toFloat() / span).coerceAtMost(1f))
        }
        step(1)
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false,
                                      dismissOnClickOutside = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF07070C))) {
            // The page steps and pauses from behind the card, so a button on
            // a question keeps its own tap.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Accent.copy(alpha = 0.28f), Color(0xFF07070C),
                                   AccentAlt.copy(alpha = 0.22f))))
                    .pointerInput(cards, index) {
                        detectTapGestures(
                            onPress = { offset ->
                                val down = System.currentTimeMillis()
                                val held = tryAwaitRelease()
                                paused = false
                                if (!held) return@detectTapGestures
                                if (System.currentTimeMillis() - down >= TAP_MS) return@detectTapGestures
                                val back = offset.x < size.width * 0.33f
                                val here = cards.getOrNull(index)
                                if (back) step(-1)
                                else if (here?.wantsTouches != true) step(1)
                            },
                            onLongPress = { paused = true },
                        )
                    },
            )

            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    cards.indices.forEach { position ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f)),
                        ) {
                            val fill = when {
                                position < index -> 1f
                                position > index -> 0f
                                else -> progress.value
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth(fill)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(Color.White),
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    StoryIcon(if (muted) "🔇" else "🔊") {
                        muted = !muted
                        session.settings.storyMuted = muted
                        player.setMuted(muted)
                    }
                    Spacer(Modifier.width(6.dp))
                    StoryIcon("×", onClose)
                }

                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = index,
                        transitionSpec = {
                            (fadeIn(tween(350)) + slideInVertically { it / 12 })
                                .togetherWith(fadeOut(tween(180)))
                        },
                        label = "card",
                    ) { position ->
                        val shown = cards.getOrNull(position)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .offset { IntOffset((9 * sin(shake.value * Math.PI * 4).toFloat()).toInt(), 0) }
                                .alpha(if (paused) 0.55f else 1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            shown?.content?.invoke(
                                StoryContext(
                                    data = data,
                                    answered = answers[position],
                                    answer = { right ->
                                        if (answers[position] == null) {
                                            answers[position] = right
                                            if (right) {
                                                confetti += 1
                                                buzz(context, success = true)
                                            } else {
                                                buzz(context, success = false)
                                            }
                                        }
                                    },
                                    skip = { step(1) },
                                    close = onClose,
                                    session = session,
                                    scoreLine = answers.values.let { given ->
                                        if (given.isEmpty()) null
                                        else context.getString(R.string.story_score)
                                            .format(given.count { it }, given.size)
                                    },
                                ),
                            )
                        }
                    }
                }
            }

            if (confetti > 0) {
                key(confetti) { Confetti() }
            }
        }
    }

    // The flinch of a wrong answer.
    LaunchedEffect(answers[index]) {
        if (answers[index] == false) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(500, easing = LinearEasing))
            shake.snapTo(0f)
        }
    }
}

@Composable
private fun StoryIcon(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 17.sp)
    }
}

private fun buzz(context: android.content.Context, success: Boolean) = runCatching {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)
            as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    val pattern = if (success) longArrayOf(0, 12, 40, 12) else longArrayOf(0, 160)
    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
}.let { }

/** Plays the songs behind the story, fading between them. */
class StoryPlayer {
    private var player: MediaPlayer? = null
    private var current: String? = null

    /** [file] already holds the middle of the song – see [Snippet]. */
    fun play(id: String, file: java.io.File?, muted: Boolean) {
        if (file == null || id == current) return
        current = id
        stop()
        runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { media ->
                    media.setVolume(0f, 0f)
                    media.isLooping = true
                    media.start()
                    if (!muted) fade(media, 0.55f)
                }
                prepareAsync()
                player = this
            }
        }
    }

    fun setMuted(muted: Boolean) {
        val media = player ?: return
        if (muted) media.setVolume(0f, 0f) else fade(media, 0.55f)
    }

    fun stop() {
        runCatching { player?.release() }
        player = null
    }

    private fun fade(media: MediaPlayer, target: Float) {
        Thread {
            var level = 0f
            while (level < target) {
                level += target / 30f
                runCatching { media.setVolume(level, level) }
                Thread.sleep(45)
            }
        }.start()
    }
}
