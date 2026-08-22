package org.rotationmusic.app.core

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import org.rotationmusic.app.MainActivity
import org.rotationmusic.app.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The one notification Rotation sends: your recap is ready.
 *
 * Scheduled locally for the first of December and re-armed for the next year
 * when it fires, so it arrives whether or not the app has been opened since –
 * and without a push server, which a self-hosted statistics app has no
 * business running.
 */
object SeasonReminder {

    private const val CHANNEL = "season"
    private const val REQUEST = 4711

    /** Called at every start: arms the alarm, or clears it when switched off. */
    fun refresh(context: Context) {
        val wanted = Prefs(context).seasonReminder
        if (wanted) schedule(context) else cancel(context)
    }

    fun schedule(context: Context) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val when_ = nextFirstOfDecember()
        // Inexact on purpose: a recap that arrives with the morning is fine,
        // and an exact alarm would need a permission the user must grant.
        alarms.setWindow(
            AlarmManager.RTC_WAKEUP, when_, 60 * 60 * 1000L, pending(context))
    }

    fun cancel(context: Context) {
        context.getSystemService<AlarmManager>()?.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST,
            Intent(context, SeasonAlarm::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun nextFirstOfDecember(): Long {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val year = if (today.monthValue < 12) today.year else today.year + 1
        return LocalDateTime.of(year, 12, 1, 10, 0)
            .atZone(zone).toInstant().toEpochMilli()
    }

    fun notify(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.season_notify_title),
                                    NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val note = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.season_notify_title))
            .setContentText(context.getString(R.string.season_notify_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(REQUEST, note) }
    }
}

/** Fires on 1 December, shows the notification and arms the next year. */
class SeasonAlarm : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Prefs(context).seasonReminder) {
            SeasonReminder.notify(context)
        }
        SeasonReminder.schedule(context)
    }
}
