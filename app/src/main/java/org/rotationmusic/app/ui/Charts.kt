package org.rotationmusic.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.rotationmusic.app.core.ClockInfo
import org.rotationmusic.app.core.DayEntry
import org.rotationmusic.app.core.Format
import org.rotationmusic.app.core.MonthEntry
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Plays per day, with a read-out that follows the finger. */
@Composable
fun TimelineChart(days: List<DayEntry>, plays: (Int) -> String) {
    if (days.isEmpty()) return
    var selected by remember(days) { mutableStateOf<Int?>(null) }
    val peak = (days.maxOfOrNull { it.plays } ?: 1).coerceAtLeast(1)
    val line = MaterialTheme.colorScheme.onSurfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(days) {
                    detectHorizontalDragGestures(
                        onDragEnd = { selected = null },
                        onDragCancel = { selected = null },
                    ) { change, _ ->
                        val ratio = change.position.x / size.width
                        selected = (ratio * (days.size - 1)).roundToInt()
                            .coerceIn(0, days.size - 1)
                    }
                }
                .pointerInput(days) {
                    detectTapGestures { position ->
                        val ratio = position.x / size.width
                        selected = (ratio * (days.size - 1)).roundToInt()
                            .coerceIn(0, days.size - 1)
                    }
                }
        ) {
            val step = if (days.size > 1) size.width / (days.size - 1) else 0f
            val height = size.height
            fun y(value: Int) = height - (value.toFloat() / peak) * (height * 0.86f)

            val path = Path().apply {
                days.forEachIndexed { index, day ->
                    val x = index * step
                    if (index == 0) moveTo(x, y(day.plays)) else lineTo(x, y(day.plays))
                }
            }
            val area = Path().apply {
                addPath(path)
                lineTo((days.size - 1) * step, height)
                lineTo(0f, height)
                close()
            }
            drawPath(area, Brush.verticalGradient(
                listOf(Accent.copy(alpha = 0.38f), Color.Transparent)))
            drawPath(path, Accent, style = Stroke(width = 4f, cap = StrokeCap.Round))
            drawLine(line.copy(alpha = 0.25f), Offset(0f, height), Offset(size.width, height))

            selected?.let { index ->
                val x = index * step
                drawLine(line.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, height),
                    strokeWidth = 2f)
                drawCircle(Accent, radius = 9f, center = Offset(x, y(days[index].plays)))
            }
        }
        Text(
            selected?.let { index ->
                "${Format.day(days[index].date, long = true)} · ${plays(days[index].plays)}"
            } ?: "",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().height(18.dp),
        )
    }
}

/** The hours of the day drawn as a record: each spoke one hour. */
@Composable
fun RecordClock(clock: ClockInfo, plays: (Int) -> String) {
    val hours = clock.hours
    if (hours.none { it > 0 }) return
    var touched by remember(clock) { mutableStateOf<Int?>(null) }
    val shown = touched ?: clock.peakHour
    val peak = (hours.maxOrNull() ?: 1).coerceAtLeast(1)
    val dial = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val hub = MaterialTheme.colorScheme.onSurfaceVariant

    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .size(240.dp)
                .pointerInput(clock) {
                    detectDragGesturesAfterLongPress(
                        onDragEnd = { touched = null },
                        onDragCancel = { touched = null },
                    ) { change, _ ->
                        touched = hourAt(change.position, size.width.toFloat(), size.height.toFloat())
                    }
                }
                .pointerInput(clock) {
                    detectTapGestures { position ->
                        touched = hourAt(position, size.width.toFloat(), size.height.toFloat())
                    }
                }
        ) {
            val centre = Offset(size.width / 2, size.height / 2)
            val outer = min(size.width, size.height) * 0.40f
            val inner = min(size.width, size.height) * 0.16f

            drawCircle(dial, radius = outer, center = centre, style = Stroke(width = 2f))
            drawCircle(dial, radius = inner, center = centre, style = Stroke(width = 2f))

            hours.forEachIndexed { hour, value ->
                val fraction = value.toFloat() / peak
                val angle = (hour / 24.0 * 2 * Math.PI - Math.PI / 2).toFloat()
                val length = inner + (outer - inner) * fraction
                drawLine(
                    Accent.copy(alpha = if (shown == hour) 1f else 0.25f + fraction * 0.7f),
                    Offset(centre.x + cos(angle) * inner, centre.y + sin(angle) * inner),
                    Offset(centre.x + cos(angle) * length, centre.y + sin(angle) * length),
                    strokeWidth = size.width * 0.026f,
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(hub.copy(alpha = 0.4f), radius = size.width * 0.017f, center = centre)
        }
        Text(
            shown?.let { "${Format.hour(it)} · ${plays(hours.getOrElse(it) { 0 })}" } ?: "",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun hourAt(position: Offset, width: Float, height: Float): Int {
    val dx = position.x - width / 2
    val dy = position.y - height / 2
    var angle = atan2(dy, dx) + (Math.PI / 2).toFloat()
    if (angle < 0) angle += (2 * Math.PI).toFloat()
    return ((angle / (2 * Math.PI) * 24).roundToInt()) % 24
}

/** Weekday against hour of day. */
@Composable
fun Heatmap(matrix: List<List<Int>>) {
    if (matrix.isEmpty()) return
    val peak = (matrix.flatten().maxOrNull() ?: 1).coerceAtLeast(1)
    val empty = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        matrix.take(7).forEachIndexed { day, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Format.weekdays.getOrElse(day) { "" },
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(26.dp),
                )
                (0 until 24).forEach { hour ->
                    val value = row.getOrElse(hour) { 0 }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (value == 0) empty
                                else Accent.copy(alpha = 0.18f + value.toFloat() / peak * 0.82f)
                            )
                    )
                }
            }
        }
        Row {
            Spacer(Modifier.width(26.dp))
            listOf(0, 6, 12, 18).forEach { hour ->
                Text(
                    "$hour",
                    fontSize = 9.sp,
                    maxLines = 1,
                    softWrap = false,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(6f),
                )
            }
        }
    }
}

/** The twelve months of a Wrapped year. */
@Composable
fun MonthBars(months: List<MonthEntry>, selected: Int?, onSelect: (Int) -> Unit,
              plays: (Int) -> String) {
    if (months.isEmpty()) return
    val peak = (months.maxOfOrNull { it.plays } ?: 1).coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .pointerInput(months) {
                    detectTapGestures { position ->
                        onSelect(((position.x / size.width) * 12).toInt().coerceIn(0, 11) + 1)
                    }
                }
                .pointerInput(months) {
                    detectHorizontalDragGestures { change, _ ->
                        onSelect(((change.position.x / size.width) * 12).toInt()
                            .coerceIn(0, 11) + 1)
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            months.forEach { month ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(
                            (month.plays.toFloat() / peak).coerceAtLeast(0.03f))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(Accent.copy(
                            alpha = if (selected == month.month) 1f else 0.55f))
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Format.months.forEach { name ->
                Text(
                    name.take(1),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        selected?.let { month ->
            val entry = months.getOrNull(month - 1)
            if (entry != null) {
                Text(
                    "${Format.months.getOrElse(month - 1) { "" }} · ${plays(entry.plays)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
