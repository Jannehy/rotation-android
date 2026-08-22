package org.rotationmusic.app.core

import android.content.Context
import android.text.format.DateUtils
import org.rotationmusic.app.R
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Format {

    /** "2 h 15 min", "3 d 4 h" – never "2 h 60 min". */
    fun duration(seconds: Int): String {
        var hours = seconds / 3600
        var minutes = Math.round((seconds % 3600) / 60f)
        if (minutes == 60) { hours += 1; minutes = 0 }
        return when {
            hours >= 24 -> "${hours / 24} d ${hours % 24} h"
            hours > 0 -> "$hours h $minutes min"
            else -> "$minutes min"
        }
    }

    fun number(value: Int): String = NumberFormat.getIntegerInstance().format(value)

    fun ago(timestamp: Long): CharSequence =
        DateUtils.getRelativeTimeSpanString(
            timestamp * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)

    fun day(iso: String, long: Boolean = false): String = try {
        val date = LocalDate.parse(iso)
        date.format(DateTimeFormatter.ofPattern(if (long) "EEEE, d. MMMM" else "d. MMM"))
    } catch (e: Exception) {
        iso
    }

    fun date(timestamp: Long): String =
        Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofPattern("d. MMM yyyy"))

    fun hour(hour: Int): String = String.format(Locale.getDefault(), "%02d:00", hour)

    /** Monday first, matching the server's week. */
    val weekdays: List<String> = (1..7).map {
        java.time.DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

    val months: List<String> = (1..12).map {
        java.time.Month.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

    fun plays(context: Context, count: Int): String =
        context.getString(R.string.plays_count, count)
}
