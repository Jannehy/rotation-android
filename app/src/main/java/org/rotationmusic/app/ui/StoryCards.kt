package org.rotationmusic.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.rotationmusic.app.R
import org.rotationmusic.app.core.Format
import org.rotationmusic.app.core.Session
import org.rotationmusic.app.core.TrackEntry
import org.rotationmusic.app.core.Wrapped
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** What a card needs to know about the year it is part of. */
data class StoryContext(
    val data: Wrapped,
    /** null while unanswered, then whether the guess was right. */
    val answered: Boolean?,
    val answer: (Boolean) -> Unit,
    /** Moves on without counting the question at all. */
    val skip: () -> Unit,
    val close: () -> Unit,
    val session: Session,
    /** "2 of 2 questions right", or null when nothing was asked. */
    val scoreLine: String? = null,
)

/**
 * One screen of the story. [music] is set on the four cards that bring a song
 * with them; the rest let whatever is playing run on.
 */
class StoryCard(
    val music: TrackEntry? = null,
    val isQuestion: Boolean = false,
    /** The last card stays put: it holds the picture and the buttons. */
    val isFinale: Boolean = false,
    val content: @Composable (StoryContext) -> Unit,
) {
    /** Cards holding something to press keep their touches; the rest let
     *  them through to the page behind, which does the stepping. */
    val wantsTouches: Boolean get() = isQuestion || isFinale
}

fun storyCards(data: Wrapped): List<StoryCard> {
    val cards = mutableListOf<StoryCard>()
    val artists = data.artists
    val tracks = data.tracks

    cards += StoryCard(music = openingTrack(data)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
               verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "${data.year}",
                fontSize = 88.sp, lineHeight = 92.sp, fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.displayLarge.copy(
                    brush = Brush.linearGradient(listOf(Accent, AccentAlt))),
            )
            StoryTitle(stringResource(R.string.story_intro))
            StorySub(stringResource(R.string.story_intro_sub))
        }
    }

    cards += StoryCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
               verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StoryKicker(stringResource(R.string.story_minutes))
            CountUp(data.summary.seconds / 60)
            StorySub(stringResource(R.string.story_minutes_sub)
                .format(Format.number(data.summary.plays),
                        Format.number(data.summary.activeDays)))
        }
    }

    val first = artists.firstOrNull()
    if (artists.size >= 4 && first != null) {
        val decoys = decoys(artists.drop(1).map { it.name }, 3)
        cards += StoryCard(isQuestion = true) { context ->
            QuizCard(stringResource(R.string.story_quiz_artist),
                     first.name, decoys, numeric = false, context = context)
        }
        cards += StoryCard(music = data.artistTrack) { context ->
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                   verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(first.art, first.name, 200.dp, context.session, round = true)
                StoryKicker(stringResource(R.string.story_artist_one))
                StoryTitle(first.name)
                StorySub("${Format.number(first.plays)} " +
                    stringResource(R.string.unit_plays) + " · " +
                    stringResource(R.string.story_devotion).format(data.devotion))
            }
        }
    }

    if (artists.isNotEmpty()) {
        cards += StoryCard { context ->
            StoryList(stringResource(R.string.card_artists), context.session,
                artists.take(5).map {
                    StoryRow(it.name,
                             "${Format.number(it.plays)} " +
                                 stringResource(R.string.unit_plays),
                             it.art, round = true)
                })
        }
    }

    val best = tracks.firstOrNull()
    if (tracks.size >= 2 && best != null) {
        cards += StoryCard(isQuestion = true) { context ->
            QuizCard(stringResource(R.string.story_quiz_plays),
                     "${best.plays}", guesses(best.plays),
                     numeric = true, context = context)
        }
        cards += StoryCard(music = best) { context ->
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                   verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(best.art, best.title, 200.dp, context.session)
                StoryKicker(stringResource(R.string.story_track_one))
                StoryTitle(best.title)
                StorySub("${best.artist} · ${Format.number(best.plays)} " +
                    stringResource(R.string.unit_plays))
            }
        }
    }

    if (tracks.isNotEmpty()) {
        cards += StoryCard { context ->
            StoryList(stringResource(R.string.card_tracks), context.session,
                tracks.take(5).map { StoryRow(it.title, it.artist, it.art) })
        }
    }

    val genre = data.genres.firstOrNull()
    if (genre != null || data.clock.peakHour != null) {
        cards += StoryCard(music = data.genreTrack) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                   verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (genre != null) {
                    StoryKicker(stringResource(R.string.story_genre))
                    StoryTitle(genre.name)
                }
                data.clock.peakHour?.let { hour ->
                    Spacer(Modifier.height(10.dp))
                    StoryKicker(stringResource(R.string.story_hour))
                    Text(Format.hour(hour), fontSize = 56.sp,
                         fontWeight = FontWeight.Black, color = Color.White)
                }
            }
        }
    }

    if (data.streak > 0) {
        cards += StoryCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                   verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StoryKicker(stringResource(R.string.story_streak))
                CountUp(data.streak, grouping = false)
                StorySub(stringResource(R.string.days_count).format(data.streak))
            }
        }
    }

    if (data.discoveries.isNotEmpty()) {
        cards += StoryCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                   verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StoryKicker(stringResource(R.string.story_new))
                FlowRowChips(data.discoveries.take(8).map { it.name })
            }
        }
    }

    data.previous?.takeIf { it.plays > 0 }?.let { previous ->
        val delta = ((data.summary.plays - previous.plays) * 100f / previous.plays).roundToInt()
        cards += StoryCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                   verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StoryKicker(stringResource(R.string.story_previous).format(previous.year))
                Text("${if (delta >= 0) "+" else ""}${Format.number(delta)} %",
                     fontSize = 56.sp, fontWeight = FontWeight.Black, color = Color.White)
                StorySub("${Format.number(previous.plays)} → " +
                    "${Format.number(data.summary.plays)} " +
                    stringResource(R.string.unit_plays))
            }
        }
    }

    cards += StoryCard(isFinale = true) { context -> StoryFinale(data, context) }
    return cards
}

/* ── Choosing ─────────────────────────────────────────────────── */

/** The opening song: not the winner, and not its artist's either. */
private fun openingTrack(data: Wrapped): TrackEntry? {
    val banned = setOfNotNull(data.tracks.firstOrNull()?.id, data.artistTrack?.id)
    return data.tracks.filterNot { it.id in banned }.randomOrNull()
        ?: data.tracks.firstOrNull()
}

/** Three other names, spread over the list so the answer is not obvious. */
private fun decoys(names: List<String>, count: Int): List<String> {
    val unique = names.distinct()
    if (unique.size <= count) return unique
    val step = (unique.size / count).coerceAtLeast(1)
    return unique.filterIndexed { index, _ -> index % step == 0 }.take(count)
}

/** Plausible wrong counts around the real one. */
private fun guesses(real: Int): List<String> =
    listOf(0.45, 0.7, 1.6, 2.3)
        .map { (real * it).roundToInt().coerceAtLeast(1) }
        .filter { it != real }
        .distinct().take(3).map { "$it" }

/* ── The pieces a card is made of ─────────────────────────────── */

@Composable
fun StoryTitle(text: String) {
    Text(text, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black,
         color = Color.White, textAlign = TextAlign.Center)
}

@Composable
fun StoryKicker(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium,
         fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
}

@Composable
fun StorySub(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium,
         color = Color.White.copy(alpha = 0.78f), textAlign = TextAlign.Center)
}

/** A number that arrives rather than appears. */
@Composable
fun CountUp(target: Int, grouping: Boolean = true) {
    var shown by remember(target) { mutableIntStateOf(0) }
    LaunchedEffect(target) {
        val steps = 34
        for (step in 0..steps) {
            val share = step.toFloat() / steps
            // Fast at first, gentle at the end.
            shown = (target * (1 - (1 - share).pow(3))).roundToInt()
            delay(35)
        }
        shown = target
    }
    Text(if (grouping) Format.number(shown) else "$shown",
         fontSize = 64.sp, fontWeight = FontWeight.Black, color = Color.White)
}

data class StoryRow(val name: String, val meta: String, val art: String?,
                    val round: Boolean = false)

@Composable
fun StoryList(title: String, session: Session, rows: List<StoryRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StoryKicker(title)
        rows.forEachIndexed { index, row ->
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${index + 1}", fontFamily = FontFamily.Monospace,
                     style = MaterialTheme.typography.labelSmall,
                     color = Color.White.copy(alpha = 0.55f),
                     modifier = Modifier.width(18.dp))
                Artwork(row.art, row.name, 48.dp, session, round = row.round)
                Column(Modifier.weight(1f)) {
                    Text(row.name, color = Color.White, maxLines = 1,
                         overflow = TextOverflow.Ellipsis,
                         style = MaterialTheme.typography.bodyLarge)
                    Text(row.meta, color = Color.White.copy(alpha = 0.65f), maxLines = 1,
                         overflow = TextOverflow.Ellipsis,
                         style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun FlowRowChips(names: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp),
           horizontalAlignment = Alignment.CenterHorizontally) {
        names.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { name ->
                    Text(name, style = MaterialTheme.typography.bodyMedium,
                         color = Color.White,
                         modifier = Modifier
                             .clip(CircleShape)
                             .background(Color.White.copy(alpha = 0.12f))
                             .padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
        }
    }
}

/** A question, and what happens when it is answered. */
@Composable
fun QuizCard(question: String, right: String, others: List<String>,
             numeric: Boolean, context: StoryContext) {
    val options = remember(question) { (others + right).shuffled() }
    var picked by remember(question) { mutableStateOf<String?>(null) }

    Column(horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(question, fontSize = 24.sp, lineHeight = 29.sp,
             fontWeight = FontWeight.SemiBold, color = Color.White,
             textAlign = TextAlign.Center)

        val rows = if (numeric) options.chunked(2) else options.map { listOf(it) }
        rows.forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                line.forEach { option ->
                    val background = when {
                        picked == null -> Color.White.copy(alpha = 0.1f)
                        option == right -> Color(0xFF2AD4A4)
                        option == picked -> Accent.copy(alpha = 0.35f)
                        else -> Color.White.copy(alpha = 0.1f)
                    }
                    Text(
                        option,
                        color = if (picked != null && option == right) Color(0xFF06231C)
                                else Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(background)
                            // The whole tile answers, not only the word on it.
                            .clickable(enabled = picked == null) {
                                picked = option
                                context.answer(option == right)
                            }
                            .padding(vertical = 15.dp),
                    )
                }
            }
        }

        if (picked != null) {
            Text(if (picked == right) stringResource(R.string.story_right)
                 else stringResource(R.string.story_wrong),
                 style = MaterialTheme.typography.titleMedium, color = Color.White)
        } else {
            Text(stringResource(R.string.story_skip),
                 style = MaterialTheme.typography.bodySmall,
                 color = Color.White.copy(alpha = 0.55f),
                 modifier = Modifier.clickable { context.skip() }.padding(6.dp))
        }
    }
}

/**
 * The width the buttons under the picture share.
 *
 * They used to take three quarters of the card, which left a label that needs
 * two lines looking cramped next to the picture above it. The cap keeps a
 * tablet from drawing a button the whole width of the screen.
 */
private val storyButtonWidth = Modifier.fillMaxWidth().widthIn(max = 420.dp)

/** The last card: the picture, a way to share it, and a way out. */
@Composable
fun StoryFinale(data: Wrapped, context: StoryContext) {
    val android = LocalContext.current
    var card by remember { mutableStateOf<Bitmap?>(null) }
    val labels = CardLabels(
        kicker = "${stringResource(R.string.wrapped_year)} ${data.year}",
        topArtists = stringResource(R.string.card_artists),
        topTracks = stringResource(R.string.card_tracks),
        minutes = stringResource(R.string.card_minutes),
        topGenre = stringResource(R.string.card_topgenre),
    )
    val name = data.user?.name ?: context.session.me?.user?.name.orEmpty()

    LaunchedEffect(data.year) {
        // The artwork has to be in hand before drawing: the card is painted
        // in one pass and cannot wait for a download.
        val cover = loadArtwork(android, context.session, data.artists.firstOrNull()?.art)
        card = renderShareCard(data, name, cover, labels)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StoryKicker(stringResource(R.string.story_end).format(data.year))
        context.scoreLine?.let { score ->
            // A headline size here fights the picture below it and breaks
            // mid-sentence on a narrow screen; this is a caption, not a title.
            Text(score, fontSize = 19.sp, lineHeight = 24.sp,
                 fontWeight = FontWeight.SemiBold, color = Color.White,
                 textAlign = TextAlign.Center)
        }
        card?.let { bitmap ->
            Image(bitmap.asImageBitmap(), contentDescription = null,
                  modifier = Modifier
                      .fillMaxWidth(0.62f)
                      .clip(RoundedCornerShape(14.dp)))
            Button(onClick = { shareBitmap(android, bitmap, data.year) },
                   modifier = storyButtonWidth) {
                Text(stringResource(R.string.wrapped_share),
                     textAlign = TextAlign.Center,
                     modifier = Modifier.fillMaxWidth())
            }
        }
        WrappedPlaylistButton(data, context.session, onDark = true,
                              modifier = storyButtonWidth)
        Text(stringResource(R.string.story_close),
             style = MaterialTheme.typography.bodyMedium,
             color = Color.White.copy(alpha = 0.7f),
             modifier = Modifier.clickable { context.close() }.padding(8.dp))
    }
}

/** The confetti of a right answer, taken down again after a moment. */
@Composable
fun Confetti() {
    var gone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(3_600); gone = true }
    if (gone) return

    val colours = listOf(Accent, AccentAlt, Color(0xFF2AD4A4), Color(0xFFFFD166), Color.White)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = maxHeight
        val width = maxWidth
        repeat(70) { index ->
            // Every bit's path comes from its number, not from a fresh roll:
            // a random value would be drawn again on each redraw.
            val spread = wobble(index, 0.13f)
            val spin = 180f + wobble(index, 0.37f) * 440f
            val span = 1500 + (wobble(index, 0.71f) * 1200).toInt()
            val fall = remember(index) { Animatable(-0.05f) }
            LaunchedEffect(index) {
                delay((index % 12) * 30L)
                fall.animateTo(1.1f, tween(span, easing = LinearEasing))
            }
            Box(
                Modifier
                    .offset(x = width * spread, y = height * fall.value)
                    .size(9.dp, 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colours[index % colours.size]),
            )
        }
    }
}

/** Fetches one piece of artwork as a bitmap, through the same session Coil uses. */
suspend fun loadArtwork(
    context: android.content.Context,
    session: Session,
    art: String?,
): Bitmap? {
    val url = session.source?.artworkUrl(art, 600) ?: return null
    val request = coil.request.ImageRequest.Builder(context)
        .data(url)
        .addHeader("Cookie", (session.source as? org.rotationmusic.app.core.Api)
            ?.cookieHeader().orEmpty())
        .allowHardware(false)
        .build()
    val result = coil.ImageLoader(context).execute(request)
    return (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
}

private fun wobble(index: Int, salt: Float): Float {
    val value = sin(index * 12.9898 + salt * 78.233) * 43758.5453
    return (value - floor(value)).toFloat()
}
