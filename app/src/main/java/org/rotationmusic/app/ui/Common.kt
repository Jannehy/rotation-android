package org.rotationmusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import org.rotationmusic.app.core.Api
import org.rotationmusic.app.core.Format
import org.rotationmusic.app.core.Period
import org.rotationmusic.app.core.Session

/** A card, the way every screen in Rotation uses one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RotationCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    action: CardAction? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null || action != null) {
            // Title and buttons wrap instead of squeezing: on a narrow phone
            // three items in one row turn the words into vertical letters.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.CenterVertically))
                }
                action?.let { deed ->
                    deed.secondary?.let { label ->
                        Text(label, style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant,
                             maxLines = 1,
                             modifier = Modifier
                                 .align(Alignment.CenterVertically)
                                 .clickable { deed.onSecondary?.invoke() }
                                 .padding(4.dp))
                    }
                    OutlinedButton(onClick = deed.onRun, enabled = !deed.busy,
                                   contentPadding = PaddingValues(horizontal = 14.dp,
                                                                  vertical = 6.dp)) {
                        if (deed.busy) CircularProgressIndicator(Modifier.size(16.dp))
                        else Text(deed.title, maxLines = 1,
                                  style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        content()
    }
}

/** One of the headline numbers above the fold. */
@Composable
fun StatTile(value: String, label: String, accent: Boolean = false, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Text(
            value,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (accent) Accent else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
fun PeriodChips(selected: Period, onSelect: (Period) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(Period.entries.size) { index ->
            val period = Period.entries[index]
            val active = period == selected
            Text(
                stringResource(period.label),
                style = MaterialTheme.typography.labelLarge,
                color = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) Accent else MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(period) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/** Cover art from the server, or a coloured stand-in – as in the demo. */
@Composable
fun Artwork(art: String?, name: String, size: Dp, session: Session, round: Boolean = false) {
    val shape = if (round) CircleShape else RoundedCornerShape(size * 0.18f)
    val url = session.source?.artworkUrl(art, (size.value * 3).toInt())
    val context = LocalContext.current

    Box(Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().background(placeholderBrush(name)))
        Text(
            name.take(1).uppercase(),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (url != null) {
            val request = ImageRequest.Builder(context)
                .data(url)
                // Artwork is behind the same session as everything else.
                .addHeader("Cookie", (session.source as? Api)?.cookieHeader().orEmpty())
                .crossfade(true)
                .build()
            SubcomposeAsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = {},
                error = {},
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** A stable colour per name, so artwork-less entries still look deliberate. */
fun placeholderBrush(text: String): Brush {
    var hash = 5381L
    text.forEach { hash = (hash * 33 + it.code) and 0xFFFFFFFFL }
    val hue = (hash % 360).toFloat()
    return Brush.linearGradient(
        listOf(
            Color.hsv(hue, 0.55f, 0.55f),
            Color.hsv((hue + 28f) % 360f, 0.65f, 0.32f),
        )
    )
}

/** One line of a top list: rank, artwork, name, count, and a share bar. */
@Composable
fun TopRow(
    rank: Int, title: String, subtitle: String, art: String?,
    plays: Int, share: Float, session: Session, round: Boolean = false,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Text(
                "$rank",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(18.dp),
            )
            Artwork(art, title, 40.dp, session, round)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                Format.number(plays),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .fillMaxWidth(share.coerceIn(0.02f, 1f))
                .height(2.dp)
                .background(Accent.copy(alpha = 0.55f), RoundedCornerShape(1.dp))
        )
    }
}

