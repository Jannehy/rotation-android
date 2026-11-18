package org.rotationmusic.app.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The wire format of the Rotation API. The demo source builds the very same
// structures locally, so nothing above this layer knows where data came from.

@Serializable
data class Person(
    val id: String,
    @SerialName("user_name") val userName: String = "",
    val name: String = "",
)

@Serializable
data class Bounds(
    @SerialName("first_play") val firstPlay: Long = 0,
    @SerialName("last_play") val lastPlay: Long = 0,
    @SerialName("history_plays") val historyPlays: Int = 0,
    @SerialName("lifetime_plays") val lifetimePlays: Int = 0,
)

@Serializable
data class MeInfo(
    val user: Person,
    val bounds: Bounds = Bounds(),
    val years: List<Int> = emptyList(),
    val timezone: String = "",
    val discoverable: Boolean = true,
    val season: Season = Season(),
    val version: String = "",
)

/**
 * When the recap steps forward by itself: 1 December to 31 January. The
 * server decides it so every client agrees on the date.
 */
@Serializable
data class Season(
    val open: Boolean = false,
    val year: Int = 0,
    val start: Long = 0,
    val end: Long = 0,
)

/** A playlist Rotation keeps up to date in Navidrome. */
@Serializable
data class ManagedPlaylist(
    val range: String = "",
    val size: Int = 0,
    val name: String = "",
    @SerialName("playlist_id") val playlistId: String = "",
    val tracks: Int = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

@Serializable
data class ManagedPlaylists(val playlists: List<ManagedPlaylist> = emptyList())

@Serializable
data class PlaylistWritten(
    val ok: Boolean = false,
    val playlist: ManagedPlaylist = ManagedPlaylist(),
    val tracks: Int = 0,
)

/**
 * The recap playlist of one year.
 *
 * Unlike a [ManagedPlaylist] this one is a snapshot: it is written when it is
 * asked for and never refreshed on its own, because a year that is over does
 * not change. [exists] says whether it has been written already.
 */
@Serializable
data class WrappedPlaylist(
    val year: Int = 0,
    val size: Int = 0,
    val exists: Boolean = false,
    val name: String = "",
    val tracks: Int = 0,
    @SerialName("created_at") val createdAt: Long = 0,
)

@Serializable
data class WrappedPlaylistWritten(
    val ok: Boolean = false,
    val playlist: WrappedPlaylist = WrappedPlaylist(),
    val tracks: Int = 0,
)

@Serializable
data class RangeInfo(
    val key: String = "",
    val label: String = "",
    val start: Long = 0,
    val end: Long = 0,
    val days: Int = 0,
)

@Serializable
data class Summary(
    val plays: Int = 0,
    val seconds: Int = 0,
    val tracks: Int = 0,
    val artists: Int = 0,
    val albums: Int = 0,
    @SerialName("active_days") val activeDays: Int = 0,
)

@Serializable
data class TrackEntry(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    val art: String? = null,
    val plays: Int = 0,
    val seconds: Int = 0,
)

@Serializable
data class ArtistEntry(
    val id: String,
    val name: String = "",
    val plays: Int = 0,
    val seconds: Int = 0,
    val tracks: Int = 0,
    val art: String? = null,
)

@Serializable
data class AlbumEntry(
    val id: String,
    val name: String = "",
    val artist: String? = null,
    val plays: Int = 0,
    val seconds: Int = 0,
    val tracks: Int = 0,
    val art: String? = null,
)

@Serializable
data class GenreEntry(val name: String, val plays: Int = 0, val seconds: Int = 0)

@Serializable
data class DayEntry(val date: String, val plays: Int = 0, val seconds: Int = 0)

@Serializable
data class ClockInfo(
    val matrix: List<List<Int>> = emptyList(),
    val hours: List<Int> = emptyList(),
    val weekdays: List<Int> = emptyList(),
    @SerialName("peak_hour") val peakHour: Int? = null,
    @SerialName("peak_weekday") val peakWeekday: Int? = null,
)

@Serializable
data class Streaks(val current: Int = 0, val longest: Int = 0)

@Serializable
data class Discovery(
    val id: String,
    val name: String = "",
    val plays: Int = 0,
    @SerialName("first_play") val firstPlay: Long = 0,
)

@Serializable
data class Overview(
    val user: Person,
    val range: RangeInfo = RangeInfo(),
    val summary: Summary = Summary(),
    val tracks: List<TrackEntry> = emptyList(),
    val artists: List<ArtistEntry> = emptyList(),
    val albums: List<AlbumEntry> = emptyList(),
    val genres: List<GenreEntry> = emptyList(),
    val timeline: List<DayEntry> = emptyList(),
    val clock: ClockInfo = ClockInfo(),
    val streaks: Streaks = Streaks(),
    val discoveries: List<Discovery> = emptyList(),
)

@Serializable
data class PlayEntry(
    @SerialName("played_at") val playedAt: Long = 0,
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    val art: String? = null,
)

@Serializable
data class RecentPlays(val plays: List<PlayEntry> = emptyList())

@Serializable
data class MonthEntry(val month: Int, val plays: Int = 0, val seconds: Int = 0)

@Serializable
data class FirstPlay(
    @SerialName("played_at") val playedAt: Long = 0,
    val title: String = "",
    val artist: String = "",
    val art: String? = null,
)

@Serializable
data class PreviousYear(val year: Int, val plays: Int = 0, val seconds: Int = 0)

@Serializable
data class Wrapped(
    val year: Int = 0,
    val years: List<Int> = emptyList(),
    val user: Person? = null,
    val summary: Summary = Summary(),
    val months: List<MonthEntry> = emptyList(),
    @SerialName("top_month") val topMonth: MonthEntry? = null,
    val clock: ClockInfo = ClockInfo(),
    val tracks: List<TrackEntry> = emptyList(),
    val artists: List<ArtistEntry> = emptyList(),
    val albums: List<AlbumEntry> = emptyList(),
    val genres: List<GenreEntry> = emptyList(),
    val discoveries: List<Discovery> = emptyList(),
    @SerialName("first_play") val firstPlay: FirstPlay? = null,
    val streak: Int = 0,
    val devotion: Int = 0,
    val previous: PreviousYear? = null,
    /** The song behind the artist card, chosen by the server from ids. */
    @SerialName("artist_track") val artistTrack: TrackEntry? = null,
    /** The song behind the genre card. */
    @SerialName("genre_track") val genreTrack: TrackEntry? = null,
    /**
     * Null where there is nothing to write: a friend's year is not ours to
     * put into playlists, and the demo has no Navidrome behind it.
     */
    val playlist: WrappedPlaylist? = null,
    @SerialName("has_data") val hasData: Boolean = false,
)

@Serializable
data class MonthPoint(val month: String = "", val plays: Int = 0)

/**
 * The answer of `/api/track`, `/api/artist` and `/api/album`.
 *
 * One structure for all three: they differ in which fields they fill, not in
 * what they mean. Plays, rank and the top lists cover the period the page was
 * opened from; first and last play, months and hours are the whole history.
 */
@Serializable
data class ItemDetail(
    val kind: String = "",
    val id: String = "",
    val title: String? = null,
    val name: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val art: String? = null,
    val plays: Int = 0,
    val seconds: Int = 0,
    @SerialName("total_plays") val totalPlays: Int = 0,
    @SerialName("first_play") val firstPlay: Long = 0,
    @SerialName("last_play") val lastPlay: Long = 0,
    val rank: Int = 0,
    val tracks: Int = 0,
    val albums: Int = 0,
    @SerialName("track_total") val trackTotal: Int = 0,
    val range: RangeInfo = RangeInfo(),
    val months: List<MonthPoint> = emptyList(),
    val hours: List<Int> = emptyList(),
    @SerialName("top_tracks") val topTracks: List<TrackEntry> = emptyList(),
    @SerialName("top_albums") val topAlbums: List<AlbumEntry> = emptyList(),
) {
    val displayName: String get() = title ?: name.orEmpty()

    val subtitle: String get() = when (kind) {
        "track" -> listOfNotNull(artist, album).filter { it.isNotEmpty() }.joinToString(" · ")
        "album" -> artist.orEmpty()
        else -> ""
    }
}

@Serializable
data class FriendLists(
    val friends: List<Person> = emptyList(),
    val incoming: List<Person> = emptyList(),
    val outgoing: List<Person> = emptyList(),
    val suggestions: List<Person> = emptyList(),
)

@Serializable
data class ComparisonSide(
    val user: Person,
    val summary: Summary = Summary(),
    val artists: List<ArtistEntry> = emptyList(),
    val tracks: List<TrackEntry> = emptyList(),
)

@Serializable
data class SharedArtist(
    val name: String,
    val art: String? = null,
    val mine: Int = 0,
    val theirs: Int = 0,
)

@Serializable
data class Comparison(
    val range: RangeInfo = RangeInfo(),
    val me: ComparisonSide,
    val them: ComparisonSide,
    val shared: List<SharedArtist> = emptyList(),
)

@Serializable
data class VersionInfo(val name: String? = null, val version: String = "")

@Serializable
data class LoginResponse(val ok: Boolean = false, val user: Person)

@Serializable
data class FriendResponse(val ok: Boolean = false, val friends: Boolean = false)

@Serializable
data class OkResponse(val ok: Boolean = false)
