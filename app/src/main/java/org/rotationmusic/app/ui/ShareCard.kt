package org.rotationmusic.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import org.rotationmusic.app.core.Format
import org.rotationmusic.app.core.Wrapped
import java.io.File
import kotlin.math.roundToInt

/** The words on the card, read from resources before drawing starts. */
data class CardLabels(
    val kicker: String,
    val topArtists: String,
    val topTracks: String,
    val minutes: String,
    val topGenre: String,
)

/**
 * The colour schemes the card can be drawn in – the same four the web
 * interface and the iOS client offer, so a shared card is recognisable
 * whatever it came from.
 */
enum class CardTheme(
    val id: String,
    val stops: List<Int>,
    val label: Int,
    val ink: Int,
) {
    SUNSET("sunset", listOf(0xFFFF5A3C.toInt(), 0xFFB3234A.toInt(), 0xFF7C6CFF.toInt()),
        0xFFFF5A3C.toInt(), 0xFFECEAF2.toInt()),
    MINT("mint", listOf(0xFF2AD4A4.toInt(), 0xFF0F766E.toInt(), 0xFF123A4F.toInt()),
        0xFF2AD4A4.toInt(), 0xFFEAF6F2.toInt()),
    NIGHT("night", listOf(0xFF4353D6.toInt(), 0xFF6C2BD9.toInt(), 0xFF140F2E.toInt()),
        0xFF8F9BFF.toInt(), 0xFFE8E9F6.toInt()),
    COAL("coal", listOf(0xFF4A4A58.toInt(), 0xFF23232E.toInt(), 0xFF0A0A0F.toInt()),
        0xFFFF5A3C.toInt(), 0xFFECEAF2.toInt()),
}

private const val WIDTH = 1080
private const val HEIGHT = 1920

/**
 * Draws the year as one picture – the same layout the iOS client and the web
 * interface produce, so a shared card looks the same wherever it came from.
 */
fun renderShareCard(
    data: Wrapped,
    name: String,
    cover: Bitmap?,
    labels: CardLabels,
    theme: CardTheme = CardTheme.SUNSET,
): Bitmap {
    val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

    paint.color = AndroidColor.parseColor("#0A0A0F")
    canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)

    // Header band with the year written across it as a pattern.
    val band = 880f
    canvas.save()
    canvas.clipRect(0f, 0f, WIDTH.toFloat(), band)
    paint.shader = LinearGradient(
        0f, 0f, WIDTH.toFloat(), band,
        theme.stops.toIntArray(),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, WIDTH.toFloat(), band, paint)
    paint.shader = null

    paint.typeface = bold
    paint.textSize = 260f
    paint.color = AndroidColor.argb(33, 255, 255, 255)
    canvas.save()
    canvas.rotate(-10f, WIDTH / 2f, band / 2f)
    for (row in -1..3) {
        canvas.drawText("${data.year} ${data.year}", -160f, 170f + row * 250f, paint)
    }
    canvas.restore()
    canvas.restore()

    // The artist of the year, as artwork.
    val coverSize = 430f
    val coverLeft = (WIDTH - coverSize) / 2f
    val coverTop = 210f
    val rect = RectF(coverLeft, coverTop, coverLeft + coverSize, coverTop + coverSize)
    if (cover != null) {
        val rounded = Path().apply { addRoundRect(rect, 24f, 24f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(rounded)
        // A fresh paint: the one above still carries the pattern's alpha,
        // and drawBitmap would apply it to the artwork.
        canvas.drawBitmap(cover, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    } else {
        paint.color = AndroidColor.parseColor("#14141D")
        canvas.drawRoundRect(rect, 24f, 24f, paint)
        paint.color = AndroidColor.argb(220, 255, 255, 255)
        paint.textSize = 180f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            (data.artists.firstOrNull()?.name ?: name).take(1).uppercase(),
            rect.centerX(), rect.centerY() + 62f, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    paint.textAlign = Paint.Align.CENTER
    paint.color = AndroidColor.argb(220, 255, 255, 255)
    paint.typeface = regular
    paint.textSize = 34f
    canvas.drawText(labels.kicker.uppercase(), WIDTH / 2f, 130f, paint)
    paint.typeface = bold
    paint.textSize = 62f
    paint.color = AndroidColor.WHITE
    canvas.drawText(ellipsise(paint, name, WIDTH - 200f), WIDTH / 2f, 800f, paint)
    paint.textAlign = Paint.Align.LEFT

    // Two columns, the way everybody reads this kind of card.
    fun column(title: String, items: List<String>, x: Float, top: Float, columnWidth: Float) {
        paint.typeface = bold
        paint.textSize = 30f
        paint.color = theme.label
        canvas.drawText(title.uppercase(), x, top, paint)
        items.take(5).forEachIndexed { index, item ->
            val y = top + 76f + index * 74f
            paint.color = AndroidColor.argb(128,
                AndroidColor.red(theme.ink), AndroidColor.green(theme.ink),
                AndroidColor.blue(theme.ink))
            paint.textSize = 40f
            canvas.drawText("${index + 1}.", x, y, paint)
            paint.color = theme.ink
            canvas.drawText(ellipsise(paint, item, columnWidth - 70f), x + 62f, y, paint)
        }
    }
    column(labels.topArtists, data.artists.map { it.name }, 84f, 1010f, 420f)
    column(labels.topTracks, data.tracks.map { it.title }, 570f, 1010f, 440f)

    fun fact(title: String, value: String, x: Float, top: Float, boxWidth: Float, size: Float) {
        paint.typeface = bold
        paint.textSize = 30f
        paint.color = theme.label
        canvas.drawText(title.uppercase(), x, top, paint)
        paint.textSize = size
        paint.color = theme.ink
        canvas.drawText(ellipsise(paint, value, boxWidth), x, top + size + 14f, paint)
    }
    fact(labels.minutes, Format.number((data.summary.seconds / 60f).roundToInt()),
        84f, 1480f, 420f, 72f)
    fact(labels.topGenre, data.genres.firstOrNull()?.name ?: "—", 570f, 1480f, 440f, 62f)

    paint.typeface = regular
    paint.textSize = 30f
    paint.color = AndroidColor.argb(140,
        AndroidColor.red(theme.ink), AndroidColor.green(theme.ink),
        AndroidColor.blue(theme.ink))
    canvas.drawText("Rotation · Navidrome", 84f, HEIGHT - 70f, paint)

    return bitmap
}

private fun ellipsise(paint: Paint, text: String, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var cut = text
    while (cut.length > 1 && paint.measureText("$cut…") > maxWidth) {
        cut = cut.dropLast(1)
    }
    return "$cut…"
}

/** Hands the picture to whatever the phone offers – messages, files, anything. */
fun shareBitmap(context: Context, bitmap: Bitmap, year: Int) {
    val directory = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(directory, "rotation-$year.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.shared", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Rotation $year"))
}
