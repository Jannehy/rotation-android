package org.rotationmusic.app.core

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The demo: an invented library and a year of invented plays, aggregated on
 * the device with the same rules the server uses.
 *
 * Plays are stored as offsets from today, so the demo is always "the last few
 * weeks" no matter when the app is opened.
 */
class DemoSource private constructor(private val library: Library) : Source {

    override val isDemo = true

    @Serializable
    data class Library(
        val user: Person,
        val artists: List<Artist> = emptyList(),
        val albums: List<Album> = emptyList(),
        val tracks: List<Track> = emptyList(),
        val plays: List<List<Int>> = emptyList(),
        val friends: List<Friend> = emptyList(),
    ) {
        @Serializable data class Artist(val id: String, val name: String)
        @Serializable data class Album(val id: String, val name: String, val artist: String)
        @Serializable data class Track(
            val id: String, val title: String, val artist: String,
            val album: String, val genre: String, val seconds: Int,
        )
        @Serializable data class Friend(
            val id: String, val name: String, val plays: Int,
            val seconds: Int, @SerialName("artists") val artists: List<String> = emptyList(),
        )
    }

    private data class Play(val timestamp: Long, val track: Library.Track)

    private val zone: ZoneId = ZoneId.systemDefault()
    private val artistsById = library.artists.associateBy { it.id }
    private val albumsById = library.albums.associateBy { it.id }

    private val events: List<Play> = run {
        val midnight = LocalDate.now(zone).atStartOfDay(zone).toEpochSecond()
        library.plays.mapNotNull { entry ->
            if (entry.size != 3 || entry[2] >= library.tracks.size) null
            else Play(midnight + entry[0] * 86_400L + entry[1], library.tracks[entry[2]])
        }
    }

    companion object {
        fun load(context: Context): DemoSource? = runCatching {
            val text = context.assets.open("demo.json").bufferedReader().use { it.readText() }
            DemoSource(Json { ignoreUnknownKeys = true }.decodeFromString(text))
        }.getOrNull()
    }

    // -- time -------------------------------------------------------------

    private fun endOfToday(): Long = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toEpochSecond()

    private fun startOfYear(year: Int): Long =
        LocalDate.of(year, 1, 1).atStartOfDay(zone).toEpochSecond()

    private fun bounds(period: Period): Triple<Long, Long, Int> {
        val end = endOfToday()
        return when (period) {
            Period.WEEK -> Triple(end - 7 * 86_400L, end, 7)
            Period.MONTH -> Triple(end - 30 * 86_400L, end, 30)
            Period.QUARTER -> Triple(end - 90 * 86_400L, end, 90)
            Period.YEAR -> {
                val start = startOfYear(LocalDate.now(zone).year)
                Triple(start, end, (((end - start) / 86_400L).toInt()).coerceAtLeast(1))
            }
            Period.ALL -> {
                val start = events.firstOrNull()?.timestamp ?: end
                Triple(start, end, (((end - start) / 86_400L).toInt()).coerceAtLeast(1))
            }
        }
    }

    private fun local(timestamp: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), zone)

    private fun slice(start: Long, end: Long) =
        events.filter { it.timestamp in start until end }

    // -- aggregation ------------------------------------------------------

    private fun summary(plays: List<Play>) = Summary(
        plays = plays.size,
        seconds = plays.sumOf { it.track.seconds },
        tracks = plays.map { it.track.id }.toSet().size,
        artists = plays.map { it.track.artist }.toSet().size,
        albums = plays.map { it.track.album }.toSet().size,
        activeDays = plays.map { local(it.timestamp).toLocalDate() }.toSet().size,
    )

    private fun <T> ranked(
        plays: List<Play>, limit: Int, key: (Play) -> String, make: (String, List<Play>) -> T,
    ): List<T> = plays.groupBy(key)
        .entries
        .sortedByDescending { it.value.size }
        .take(limit)
        .map { make(it.key, it.value) }

    private fun topTracks(plays: List<Play>, limit: Int) = ranked(plays, limit, { it.track.id }) { id, group ->
        val track = group.first().track
        TrackEntry(
            id = id, title = track.title,
            artist = artistsById[track.artist]?.name.orEmpty(),
            album = albumsById[track.album]?.name,
            plays = group.size, seconds = group.size * track.seconds,
        )
    }

    private fun topArtists(plays: List<Play>, limit: Int) =
        ranked(plays, limit, { it.track.artist }) { id, group ->
            ArtistEntry(
                id = id, name = artistsById[id]?.name ?: id,
                plays = group.size, seconds = group.sumOf { it.track.seconds },
                tracks = group.map { it.track.id }.toSet().size,
            )
        }

    private fun topAlbums(plays: List<Play>, limit: Int) =
        ranked(plays, limit, { it.track.album }) { id, group ->
            val album = albumsById[id]
            AlbumEntry(
                id = id, name = album?.name ?: id,
                artist = artistsById[album?.artist]?.name,
                plays = group.size, seconds = group.sumOf { it.track.seconds },
                tracks = group.map { it.track.id }.toSet().size,
            )
        }

    private fun topGenres(plays: List<Play>, limit: Int) =
        ranked(plays, limit, { it.track.genre }) { name, group ->
            GenreEntry(name, group.size, group.sumOf { it.track.seconds })
        }

    private fun timeline(plays: List<Play>, start: Long, end: Long): List<DayEntry> {
        val buckets = plays.groupBy { local(it.timestamp).toLocalDate() }
        val days = mutableListOf<DayEntry>()
        var day = local(start).toLocalDate()
        val last = local(end - 1).toLocalDate()
        while (!day.isAfter(last)) {
            val group = buckets[day].orEmpty()
            days += DayEntry(day.toString(), group.size, group.sumOf { it.track.seconds })
            day = day.plusDays(1)
        }
        return days
    }

    private fun clock(plays: List<Play>): ClockInfo {
        val matrix = MutableList(7) { MutableList(24) { 0 } }
        val hours = MutableList(24) { 0 }
        val weekdays = MutableList(7) { 0 }
        plays.forEach {
            val moment = local(it.timestamp)
            val weekday = moment.dayOfWeek.value - 1     // Monday = 0
            matrix[weekday][moment.hour]++
            hours[moment.hour]++
            weekdays[weekday]++
        }
        return ClockInfo(
            matrix = matrix, hours = hours, weekdays = weekdays,
            peakHour = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 },
            peakWeekday = weekdays.indices.maxByOrNull { weekdays[it] }?.takeIf { weekdays[it] > 0 },
        )
    }

    private fun streaks(plays: List<Play>): Streaks {
        val days = plays.map { local(it.timestamp).toLocalDate().toEpochDay() }.toSortedSet().toList()
        if (days.isEmpty()) return Streaks()
        var longest = 1
        var run = 1
        for (index in 1 until days.size) {
            run = if (days[index] == days[index - 1] + 1) run + 1 else 1
            longest = maxOf(longest, run)
        }
        val today = LocalDate.now(zone).toEpochDay()
        var current = 0
        if (days.last() == today || days.last() == today - 1) {
            current = 1
            var cursor = days.last()
            for (index in days.size - 2 downTo 0) {
                if (days[index] == cursor - 1) { current++; cursor = days[index] } else break
            }
        }
        return Streaks(current, longest)
    }

    private fun discoveries(start: Long, end: Long, limit: Int): List<Discovery> {
        val first = mutableMapOf<String, Long>()
        val inRange = mutableMapOf<String, Int>()
        events.forEach { play ->
            val artist = play.track.artist
            first[artist] = minOf(first[artist] ?: Long.MAX_VALUE, play.timestamp)
            if (play.timestamp in start until end) inRange[artist] = (inRange[artist] ?: 0) + 1
        }
        return first.filter { it.value in start until end }
            .map { Discovery(it.key, artistsById[it.key]?.name ?: it.key, inRange[it.key] ?: 0, it.value) }
            .sortedByDescending { it.plays }
            .take(limit)
    }

    // -- Source -----------------------------------------------------------

    override suspend fun me(): MeInfo {
        // Not toSortedSet().reversed(): from API 35 android.jar carries
        // SequencedCollection, so reversed() binds to the Java member and
        // hands back a set instead of the list this wants.
        val years = events.map { local(it.timestamp).year }.distinct().sortedDescending()
        return MeInfo(
            user = library.user,
            bounds = Bounds(
                firstPlay = events.firstOrNull()?.timestamp ?: 0,
                lastPlay = events.lastOrNull()?.timestamp ?: 0,
                historyPlays = events.size, lifetimePlays = events.size,
            ),
            years = years, timezone = zone.id, discoverable = true,
            // Always in season: a reviewer opening this in June should still
            // find the recap where the description says it is.
            season = Season(open = true, year = years.firstOrNull() ?: 0),
            version = "demo",
        )
    }

    override suspend fun overview(period: Period, user: String?): Overview {
        val (start, end, days) = bounds(period)
        val plays = slice(start, end)
        return Overview(
            user = library.user,
            range = RangeInfo(period.key, period.key, start, end, days),
            summary = summary(plays),
            tracks = topTracks(plays, 25),
            artists = topArtists(plays, 25),
            albums = topAlbums(plays, 25),
            genres = topGenres(plays, 10),
            timeline = timeline(plays, start, end),
            clock = clock(plays),
            streaks = streaks(events),
            discoveries = discoveries(start, end, 12),
        )
    }

    override suspend fun recent(limit: Int, user: String?) = RecentPlays(
        events.takeLast(limit).reversed().map {
            PlayEntry(
                playedAt = it.timestamp, id = it.track.id, title = it.track.title,
                artist = artistsById[it.track.artist]?.name.orEmpty(),
                album = albumsById[it.track.album]?.name,
            )
        }
    )

    override suspend fun wrapped(year: Int?, user: String?): Wrapped {
        val target = year ?: LocalDate.now(zone).year
        val start = startOfYear(target)
        val end = minOf(startOfYear(target + 1), endOfToday())
        val plays = slice(start, end)
        val months = (1..12).map { month ->
            val group = plays.filter { local(it.timestamp).monthValue == month }
            MonthEntry(month, group.size, group.sumOf { it.track.seconds })
        }
        val artists = topArtists(plays, 10)
        val previous = slice(startOfYear(target - 1), start)
        val first = plays.firstOrNull()
        return Wrapped(
            year = target,
            years = events.map { local(it.timestamp).year }.distinct().sortedDescending(),
            user = library.user,
            summary = summary(plays),
            months = months,
            topMonth = months.maxByOrNull { it.plays },
            clock = clock(plays),
            tracks = topTracks(plays, 10),
            artists = artists,
            albums = topAlbums(plays, 10),
            genres = topGenres(plays, 8),
            discoveries = discoveries(start, end, 12),
            firstPlay = first?.let {
                FirstPlay(it.timestamp, it.track.title,
                    artistsById[it.track.artist]?.name.orEmpty())
            },
            streak = streaks(plays).longest,
            devotion = if (plays.isNotEmpty())
                ((artists.firstOrNull()?.plays ?: 0) * 100f / plays.size).roundToInt() else 0,
            previous = if (previous.isEmpty()) null
            else PreviousYear(target - 1, previous.size, previous.sumOf { it.track.seconds }),
            artistTrack = artists.firstOrNull()?.let { artist ->
                topTracks(plays.filter { it.track.artist == artist.id }, 1).firstOrNull()
            },
            genreTrack = topGenres(plays, 1).firstOrNull()?.let { genre ->
                topTracks(plays.filter { it.track.genre == genre.name }, 1).firstOrNull()
            },
            hasData = plays.isNotEmpty(),
        )
    }

    // -- one item, in detail ----------------------------------------------

    /** The window behind a range key: a period, or a plain year from the recap. */
    private fun window(range: String): Triple<Long, Long, String> {
        val period = Period.entries.firstOrNull { it.key == range }
        if (period != null) {
            val (start, end, _) = bounds(period)
            return Triple(start, end, period.key)
        }
        val year = range.toIntOrNull()
        if (year != null && year > 1900) {
            return Triple(startOfYear(year), minOf(startOfYear(year + 1), endOfToday()), range)
        }
        val (start, end, _) = bounds(Period.MONTH)
        return Triple(start, end, Period.MONTH.key)
    }

    override suspend fun detail(
        kind: String, id: String, range: String, user: String?,
    ): ItemDetail {
        val (start, end, key) = window(range)
        val history = when (kind) {
            "track" -> events.filter { it.track.id == id }
            "artist" -> events.filter { it.track.artist == id }
            else -> events.filter { it.track.album == id }
        }
        if (history.isEmpty()) throw SourceException.Server("unknown")
        val scoped = history.filter { it.timestamp in start until end }

        val months = history
            .groupingBy { local(it.timestamp).let { at -> "%04d-%02d".format(at.year, at.monthValue) } }
            .eachCount().toSortedMap()
            .map { (month, plays) -> MonthPoint(month, plays) }
        val hours = IntArray(24)
        history.forEach { hours[local(it.timestamp).hour]++ }

        // The rank is counted the way the top lists are sorted, so the number
        // here is the line the name was tapped on.
        val key3: (Play) -> String = when (kind) {
            "track" -> { play -> play.track.id }
            "artist" -> { play -> play.track.artist }
            else -> { play -> play.track.album }
        }
        val totals = slice(start, end).groupBy(key3)
        val mine = scoped.size
        val mineSeconds = scoped.sumOf { it.track.seconds }
        val rank = if (mine == 0) 0 else 1 + totals.count { (_, group) ->
            val plays = group.size
            plays > mine || (plays == mine && group.sumOf { it.track.seconds } > mineSeconds)
        }

        val track = history.first().track
        val album = albumsById[track.album]
        return ItemDetail(
            kind = kind,
            id = id,
            title = if (kind == "track") track.title else null,
            name = when (kind) {
                "artist" -> artistsById[id]?.name ?: id
                "album" -> album?.name ?: id
                else -> null
            },
            artist = when (kind) {
                "track" -> artistsById[track.artist]?.name
                "album" -> artistsById[album?.artist]?.name
                else -> null
            },
            album = if (kind == "track") album?.name else null,
            plays = mine,
            seconds = mineSeconds,
            totalPlays = history.size,
            firstPlay = history.first().timestamp,
            lastPlay = history.last().timestamp,
            rank = rank,
            tracks = scoped.map { it.track.id }.toSet().size,
            albums = scoped.map { it.track.album }.toSet().size,
            trackTotal = if (kind == "album") library.tracks.count { it.album == id } else 0,
            range = RangeInfo(key, key, start, end,
                (((end - start) / 86_400L).toInt()).coerceAtLeast(1)),
            months = months,
            hours = hours.toList(),
            topTracks = if (kind == "track") emptyList() else topTracks(scoped, 10),
            topAlbums = if (kind == "artist") topAlbums(scoped, 6) else emptyList(),
        )
    }

    override suspend fun friends() = FriendLists(
        friends = library.friends.map { Person(it.id, it.name, it.name) })

    override suspend fun addFriend(userId: String) = true
    override suspend fun removeFriend(userId: String, withdraw: Boolean) {}
    override suspend fun setDiscoverable(value: Boolean) {}
    override fun artworkUrl(art: String?, size: Int): String? = null

    // The invented library has no audio and no Navidrome behind it.
    override fun streamUrl(trackId: String?): String? = null
    override suspend fun playlists(): List<ManagedPlaylist> = emptyList()
    override suspend fun writePlaylist(range: String, language: String): PlaylistWritten =
        throw SourceException.Forbidden
    override suspend fun dropPlaylist(range: String) {}

    override suspend fun compare(userId: String, period: Period): Comparison {
        val (start, end, days) = bounds(period)
        val plays = slice(start, end)
        val mine = topArtists(plays, 25)
        val friend = library.friends.firstOrNull { it.id == userId }
            ?: throw SourceException.Forbidden
        // The friend's numbers are scaled from the demo library, so both sides
        // stay in a believable relation whatever period is picked.
        val factor = friend.plays.toFloat() / events.size.coerceAtLeast(1)
        val theirs = mine.map { artist ->
            val boost = if (friend.artists.contains(artist.name)) 2.4f else 0.5f
            val count = (artist.plays * factor * boost).roundToInt().coerceAtLeast(1)
            artist.copy(plays = count, seconds = count * 210)
        }.sortedByDescending { it.plays }
        val theirPlays = theirs.sumOf { it.plays }
        val person = Person(friend.id, friend.name, friend.name)
        return Comparison(
            range = RangeInfo(period.key, period.key, start, end, days),
            me = ComparisonSide(library.user, summary(plays), mine.take(10), topTracks(plays, 5)),
            them = ComparisonSide(
                person,
                Summary(theirPlays, theirPlays * 215, theirs.sumOf { it.tracks },
                    theirs.size, (theirs.size - 2).coerceAtLeast(1), (days / 3).coerceAtLeast(1)),
                theirs.take(10), emptyList(),
            ),
            shared = mine.take(12).mapNotNull { artist ->
                theirs.firstOrNull { it.id == artist.id }?.let {
                    SharedArtist(artist.name, null, artist.plays, it.plays)
                }
            },
        )
    }
}
