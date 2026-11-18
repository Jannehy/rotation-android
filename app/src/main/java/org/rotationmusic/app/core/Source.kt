package org.rotationmusic.app.core

import androidx.annotation.StringRes
import org.rotationmusic.app.R

/** The periods the app offers, in one place. */
enum class Period(val key: String, @StringRes val label: Int) {
    WEEK("7d", R.string.range_7d),
    MONTH("30d", R.string.range_30d),
    QUARTER("90d", R.string.range_90d),
    YEAR("year", R.string.range_year),
    ALL("all", R.string.range_all),
}

/**
 * What a screen needs, regardless of where the numbers come from.
 *
 * [Api] answers from a Rotation server, [DemoSource] from the invented
 * library bundled with the app.
 */
interface Source {
    val isDemo: Boolean

    suspend fun me(): MeInfo
    suspend fun overview(period: Period, user: String? = null): Overview
    suspend fun recent(limit: Int, user: String? = null): RecentPlays
    suspend fun wrapped(year: Int?, user: String? = null): Wrapped
    suspend fun friends(): FriendLists
    suspend fun addFriend(userId: String): Boolean
    suspend fun removeFriend(userId: String, withdraw: Boolean)
    suspend fun compare(userId: String, period: Period): Comparison
    suspend fun setDiscoverable(value: Boolean)

    /**
     * One track, artist or album in detail. [range] is the period the page
     * was opened from – a [Period] key, or a plain year from the recap – so
     * the numbers match the list the name was tapped in.
     */
    suspend fun detail(
        kind: String, id: String, range: String, user: String? = null,
    ): ItemDetail

    fun artworkUrl(art: String?, size: Int): String?

    /** A track to play, or null where there is no server – as in the demo. */
    fun streamUrl(trackId: String?): String?

    /** The playlists Rotation looks after for this user. */
    suspend fun playlists(): List<ManagedPlaylist>
    /** Creates the playlist for this period, or rewrites the existing one. */
    suspend fun writePlaylist(range: String, language: String): PlaylistWritten
    /** Stops looking after it – the playlist itself stays in Navidrome. */
    suspend fun dropPlaylist(range: String)

    /**
     * Writes the year's most played songs into Navidrome, in order.
     *
     * Asking twice rewrites the same playlist instead of leaving a copy
     * behind, so the button may be pressed again without worry.
     */
    suspend fun wrappedPlaylist(year: Int, language: String): WrappedPlaylistWritten
}

sealed class SourceException(message: String? = null) : Exception(message) {
    /** Wrong credentials, or the session expired. */
    object Unauthorized : SourceException()
    /** Asking for someone who has not confirmed the friendship. */
    object Forbidden : SourceException()
    class Unreachable(cause: String? = null) : SourceException(cause)
    object NotRotation : SourceException()
    class Server(val detail: String) : SourceException(detail)

    @StringRes
    fun messageRes(): Int = when (this) {
        is Unauthorized -> R.string.error_credentials
        is Forbidden -> R.string.error_generic
        is Unreachable -> R.string.error_unreachable
        is NotRotation -> R.string.error_norotation
        is Server -> R.string.error_generic
    }
}
